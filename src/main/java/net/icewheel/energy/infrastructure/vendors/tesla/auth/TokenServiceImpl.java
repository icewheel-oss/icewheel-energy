/*
 * IceWheel Energy
 * Copyright (C) 2025 IceWheel LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package net.icewheel.energy.infrastructure.vendors.tesla.auth;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.api.web.viewmodel.TokenDetailView;
import net.icewheel.energy.config.TokenRefreshConfig;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.Token;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.TokenResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.UserMeResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.repository.TokenRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This service is like the central manager for all Tesla API access tokens for our users.
 * It handles everything from saving new tokens when a user connects their Tesla account,
 * to refreshing tokens when they are about to expire, and even revoking them when a user disconnects.
 * Its main goal is to ensure that our application always has a valid and up-to-date way
 * to communicate with the Tesla API on behalf of our users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenServiceImpl implements TokenService {

    private final TokenRepository tokenRepository;
	private final UserRepository userRepository;
	private final TeslaAuthService teslaAuthService; // This is a one-way dependency, so @Lazy is not needed.
	private final TokenRefreshConfig tokenRefreshConfig;
	private final ObjectMapper objectMapper;

	/**
	 * Saves a brand new Tesla API token for a user. This is typically called when a user
	 * first connects their Tesla account to our application.
	 *
	 * @param user The user for whom the token is being saved.
	 * @param tokenResponse The response from the Tesla API containing the new token details.
	 * @param userMeResponse The response from the Tesla API containing user details like email and full name.
	 */
    @Override
    public void saveToken(User user, TokenResponse tokenResponse, UserMeResponse userMeResponse) {
        Token token = new Token();
        token.setUser(user);

        // Populate email and name from the Tesla API response, not the SSO user.
        if (userMeResponse != null && userMeResponse.getResponse() != null) {
            token.setEmail(userMeResponse.getResponse().getEmail());
            token.setName(userMeResponse.getResponse().getFullName());
        }

        updateTokenFields(token, tokenResponse);
        tokenRepository.save(token);
        log.info("New token saved for user {}", user.getId());
    }

	/**
	 * Saves a new token that was obtained by refreshing an existing, older token.
	 * Instead of updating the old token, a new record is created. This helps keep a clear history
	 * of all tokens and their refresh cycles.
	 *
	 * @param oldToken The token that was just refreshed.
	 * @param tokenResponse The response from the Tesla API containing the new token details.
	 */
    @Override
    public void saveToken(Token oldToken, TokenResponse tokenResponse) {
		// Why: Creating a new token record on refresh adheres to an immutable token philosophy.
		// The old token is preserved, and a new one with the updated credentials is created.
		// This avoids mutating existing records and provides a clear audit trail.
        Token newToken = new Token();
        newToken.setUser(oldToken.getUser());
        newToken.setEmail(oldToken.getEmail());
        newToken.setName(oldToken.getName());
        updateTokenFields(newToken, tokenResponse);
        tokenRepository.save(newToken);
		log.info("New token created from refresh for user {}. Old token ID: {}, New token ID: {}", oldToken.getUser()
				.getId(), oldToken.getId(), newToken.getId());
    }

    /**
     * Helper method to populate token fields from a TokenResponse.
     */
	/**
	 * This is a helper method that takes the raw token information received from Tesla
	 * and populates the fields of our internal Token object. It sets the access token,
	 * refresh token, expiration times, and other relevant details.
	 *
	 * @param token The internal Token object to update.
	 * @param tokenResponse The response from the Tesla API containing the token details.
	 */
    private void updateTokenFields(Token token, TokenResponse tokenResponse) {
        token.setAccessToken(tokenResponse.getAccessToken());
        token.setRefreshToken(tokenResponse.getRefreshToken());

		// Why: Instead of calculating expiration based on the local server's clock (now + expires_in),
		// we parse the JWT once to get the exact 'exp' (expiration) claim set by the authorization server.
		// This eliminates any potential for clock skew and ensures our stored expiration is perfectly
		// synchronized with the token itself, making our expiry checks more accurate and reliable.
		try {
			SignedJWT signedJWT = SignedJWT.parse(tokenResponse.getAccessToken());
			JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
			Date expirationTime = claimsSet.getExpirationTime();
			if (expirationTime != null) {
				token.setExpiration(expirationTime.toInstant().getEpochSecond());
			}
			else {
				log.warn("JWT for user {} has no 'exp' claim. Falling back to calculated expiration.", token.getUser()
						.getId());
				token.setExpiration(Instant.now().getEpochSecond() + tokenResponse.getExpiresIn());
			}
		}
		catch (ParseException e) {
			log.warn("Could not parse access token to get 'exp' claim, falling back to calculated expiration. Error: {}", e.getMessage());
			token.setExpiration(Instant.now().getEpochSecond() + tokenResponse.getExpiresIn());
		}

        // As per Tesla docs, refresh token is valid for a long time.
        // Let's use a reasonable default like 90 days.
		token.setRefreshTokenExpiration(Instant.now().getEpochSecond() + 90L * 24 * 60 * 60);
        token.setTokenType(tokenResponse.getTokenType());
        token.setScope(tokenResponse.getScope());
    }

	/**
	 * Provides a valid (non-expired) access token for a given user.
	 * If the current token is expired or is about to expire soon, it will automatically try to refresh it
	 * by contacting the Tesla API. This ensures that any part of the application needing to talk to Tesla
	 * always gets a working token.
	 *
	 * <p><b>Important:</b> This method uses a "pessimistic lock" to prevent issues if multiple parts of the
	 * application try to refresh the same token at the same time. It ensures that only one process can refresh
	 * the token, and others will wait and then use the newly refreshed token.</p>
	 *
	 * @param userId The ID of the user whose access token is needed.
	 * @return A valid Tesla API access token.
	 * @throws IllegalStateException if the user is not found or if token refresh fails.
	 */
	@Override
	@Transactional
	public String getValidAccessToken(String userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("User not found with ID: " + userId));

		// Why: A pessimistic lock is used here to prevent a classic race condition. If two processes
		// (e.g., a web request and the background scheduler) try to refresh the same expiring token
		// simultaneously, this lock ensures that only one process can proceed. The second process
		// will wait until the first one completes its transaction, at which point it will receive
		// the newly refreshed token and avoid a failed refresh attempt. The query is limited to the
		// single most recent token.
		List<Token> tokens = tokenRepository.findByUserWithLockOrderByCreatedAtDesc(user, PageRequest.of(0, 1));
		if (tokens.isEmpty()) {
			throw new IllegalStateException("No token found for user: " + userId);
		}
		Token token = tokens.getFirst();

		// Refresh the token if it's expired or will expire within the configured threshold.
		// This buffer prevents using a token that is just about to become invalid.
		if (token.getExpiresAt()
				.isBefore(Instant.now().plus(tokenRefreshConfig.getThresholdSeconds(), ChronoUnit.SECONDS))) {
			log.info("Token for user {} is expired or expiring soon, attempting refresh.", userId);
			TokenResponse tokenResponse = teslaAuthService.refreshToken(token.getRefreshToken());

			if (tokenResponse != null) {
				// A new token record is created with the refreshed data, preserving the old one.
				saveToken(token, tokenResponse);
				log.info("Successfully refreshed token for user {}.", userId);
				return tokenResponse.getAccessToken();
			}
			else {
				// Throwing an exception is better than returning null, as it prevents silent failures
				// and makes debugging easier. The downstream services will no longer return empty objects.
				throw new IllegalStateException("Failed to refresh Tesla API token for user " + userId + ". Refresh endpoint returned no data.");
			}
		}

		// If the token is still valid, return its access token.
		return token.getAccessToken();
    }

	/**
	 * Revokes (removes) all Tesla API tokens associated with a specific user.
	 * This effectively disconnects the user's Tesla account from our application.
	 *
	 * @param user The user whose tokens are to be revoked.
	 */
    @Override
	@Transactional
    public void revokeToken(User user) {
        // Revoke all tokens for the user
        List<Token> tokens = tokenRepository.findByUser(user);
        if (!tokens.isEmpty()) {
            tokenRepository.deleteAll(tokens);
            log.info("Revoked {} token(s) for user {}", tokens.size(), user.getId());
        }
    }

	/**
	 * Checks if a user is currently connected to their Tesla account (i.e., has a valid, non-expired token).
	 * This is a quick check that doesn't involve contacting the Tesla API.
	 *
	 * @param user The user to check.
	 * @return true if the user has a valid token, false otherwise.
	 */
	@Override
	public boolean isUserConnected(User user) {
		// This provides a quick check for the UI without triggering a network call.
		// It checks if a token exists and if its expiration time is in the future.
        return tokenRepository.findFirstByUserOrderByCreatedAtDesc(user)
				.map(token -> token.getExpiresAt().isAfter(Instant.now()))
				.orElse(false);
	}

	/**
	 * Allows for manually forcing a refresh of a specific Tesla API token.
	 * This can be useful for debugging or administrative purposes.
	 * It includes a security check to ensure that the user requesting the refresh actually owns the token.
	 *
	 * @param tokenId The unique identifier of the token to refresh.
	 * @param user The user attempting to force the refresh.
	 */
	@Override
	@Transactional
	public void forceRefreshToken(UUID tokenId, User user) {
		Token token = tokenRepository.findById(tokenId)
                .orElse(null);

		if (token == null) {
			log.warn("Attempt to refresh non-existent token with ID: {}", tokenId);
			return;
		}

		// Security Check: Ensure the token belongs to the authenticated user
		if (!token.getUser().getId().equals(user.getId())) {
			log.warn("SECURITY: User '{}' attempted to refresh token '{}' belonging to another user.", user.getId(), tokenId);
			return;
		}

		try {
			TokenResponse tokenResponse = teslaAuthService.refreshToken(token.getRefreshToken());
			if (tokenResponse != null) {
				saveToken(token, tokenResponse);
			}
		}
		catch (Exception e) {
			log.error("Failed to force refresh for token ID: {}", tokenId, e);
			// Optionally, re-throw a custom exception if the controller needs to show an error message.
		}
	}

	/**
	 * Retrieves detailed information about all Tesla API tokens for a given user.
	 * This is primarily used to display token details on a user interface, such as expiration times
	 * and the specific permissions (scopes) granted by each token.
	 *
	 * @param user The user whose token details are to be retrieved.
	 * @return A list of TokenDetailView objects, each containing detailed information about a token.
	 */
	@Override
	public List<TokenDetailView> getTokenDetailsForUser(User user) {
		List<Token> tokens = tokenRepository.findByUserOrderByCreatedAtDesc(user);

		return tokens.stream().map(token -> {
			TokenDetailView view = new TokenDetailView();
			view.setToken(token);
			try {
				SignedJWT signedJWT = SignedJWT.parse(token.getAccessToken());
				JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
				Map<String, Object> claims = new LinkedHashMap<>(claimsSet.getClaims());
				claims.replaceAll((key, value) -> (value instanceof Date) ? ((Date) value).getTime() / 1000 : value);
				view.setAccessTokenClaims(claims);

				// Why: The UI now displays a pretty-printed JSON view of the token payload.
				// This logic serializes the claims map into a JSON string to populate that view.
				try {
					view.setAccessTokenPayloadJson(objectMapper.writerWithDefaultPrettyPrinter()
							.writeValueAsString(claims));
				}
				catch (JsonProcessingException e) {
					log.error("Could not serialize JWT claims to JSON for token id {}", token.getId(), e);
					view.setAccessTokenPayloadJson("{\n  \"error\": \"Could not serialize claims to JSON.\"\n}");
				}

				Object scopeClaim = claims.get("scp");
				view.setScopes(parseScopes(scopeClaim));
			}
			catch (ParseException e) {
				log.warn("Could not parse access token JWT for token id {}: {}. It may be an opaque token.", token.getId(), e.getMessage());
				view.setAccessTokenClaims(Map.of("error", "Could not parse token. It may not be a standard JWT."));
				view.setAccessTokenPayloadJson("{\n  \"error\": \"Could not parse token. It may not be a standard JWT.\"\n}");
				view.setScopes(Collections.emptyList());
			}
			return view;
		}).collect(Collectors.toList());
	}

	/**
	 * A helper method to extract individual permission strings (scopes) from the token's scope claim.
	 * The scope claim can sometimes be a single string with spaces or a list of strings.
	 *
	 * @param scopeClaim The raw scope claim object from the JWT.
	 * @return A list of individual scope strings.
	 */
	private List<String> parseScopes(Object scopeClaim) {
		if (scopeClaim instanceof String) {
			return Arrays.asList(((String) scopeClaim).split(" "));
		}
		else if (scopeClaim instanceof List) {
			@SuppressWarnings("unchecked")
			List<String> scopes = (List<String>) scopeClaim;
			return scopes;
		}
		return Collections.emptyList();
    }
}
