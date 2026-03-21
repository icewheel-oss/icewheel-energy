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

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.LinkedHashMap;
import java.util.Map;

import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.RegisteredRegion;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.PartnerAccountApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.PublicKeyApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.TeslaTokenExchangeResult;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.TokenResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.UserMeResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.security.KeypairService;
import net.icewheel.energy.infrastructure.vendors.tesla.config.TeslaApiConfig;
import net.icewheel.energy.infrastructure.vendors.tesla.exception.TeslaApiException;
import net.icewheel.energy.infrastructure.vendors.tesla.repository.RegisteredRegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TeslaAuthService {

	private static final Logger logger = LoggerFactory.getLogger(TeslaAuthService.class);

	private final TeslaApiConfig teslaApiConfig;
	private final RestClient restClient;
	private final RegisteredRegionRepository registeredRegionRepository;
	private final KeypairService keypairService;

	private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
	private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
	private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
	private static final String USERS_ME_PATH = "/api/1/users/me";
	private static final String PARTNER_ACCOUNTS_PATH = "/api/1/partner_accounts";
	private static final String PARTNER_PUBLIC_KEY_PATH = "/api/1/partner_accounts/public_key";

	public TeslaAuthService(TeslaApiConfig teslaApiConfig, RestClient restClient,
			RegisteredRegionRepository registeredRegionRepository, KeypairService keypairService) {
		this.teslaApiConfig = teslaApiConfig;
		this.restClient = restClient;
		this.registeredRegionRepository = registeredRegionRepository;
		this.keypairService = keypairService;
	}

	public String getAuthURL(String state) {
		return UriComponentsBuilder.fromHttpUrl(teslaApiConfig.getAuthUrl())
				.queryParam("client_id", teslaApiConfig.getClientId())
				.queryParam("locale", teslaApiConfig.getLocale())
				.queryParam("prompt", "login")
				.queryParam("redirect_uri", teslaApiConfig.getRedirectUri())
				.queryParam("response_type", "code")
				.queryParam("scope", teslaApiConfig.getScope())
				.queryParam("state", state)
				.toUriString();
	}

	public TeslaTokenExchangeResult exchangeCodeForToken(String code) {
		try {
			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
			params.add("grant_type", GRANT_TYPE_AUTHORIZATION_CODE);
			params.add("client_id", teslaApiConfig.getClientId());
			params.add("client_secret", teslaApiConfig.getClientSecret());
			params.add("code", code);
			params.add("audience", teslaApiConfig.getAudience());
			params.add("redirect_uri", teslaApiConfig.getRedirectUri());

			TokenResponse tokenResponse = restClient.post()
					.uri(teslaApiConfig.getTokenUrl())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(params)
					.retrieve()
					.body(TokenResponse.class);

			if (tokenResponse == null) {
				throw new TeslaApiException("Token exchange resulted in a null response from Tesla.");
			}
			UserMeResponse userMeResponse = getUsername(tokenResponse.getAccessToken());
			return new TeslaTokenExchangeResult(tokenResponse, userMeResponse);
		}
		catch (Exception e) {
			// Why: Wrap lower-level exceptions in a domain-specific exception to centralize Tesla API error handling with context.
			throw new TeslaApiException("Failed to exchange code for token", e);
		}
	}

	public TokenResponse refreshToken(String refreshToken) {
		try {
			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
			params.add("grant_type", GRANT_TYPE_REFRESH_TOKEN);
			params.add("client_id", teslaApiConfig.getClientId());
			params.add("client_secret", teslaApiConfig.getClientSecret());
			params.add("refresh_token", refreshToken);

			return restClient.post()
					.uri(teslaApiConfig.getTokenUrl())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(params)
					.retrieve()
					.body(TokenResponse.class);
		}
		catch (Exception e) {
			throw new TeslaApiException("Failed to refresh token", e);
		}
	}

	public UserMeResponse getUsername(String accessToken) {
		try {
			return restClient.get()
					.uri(teslaApiConfig.getApiBaseUrl() + USERS_ME_PATH)
					.headers(headers -> headers.setBearerAuth(accessToken))
					.retrieve()
					.body(UserMeResponse.class);
		}
		catch (Exception e) {
			throw new TeslaApiException("Failed to get username", e);
		}
	}

	public void registerPartner() {
		String domain = teslaApiConfig.getDomain();
		// Why: Tesla's API requires a publicly accessible domain for partner registration.
		// This check prevents failed API calls during local development and provides
		// a clear error message to the developer.
		if ("localhost".equalsIgnoreCase(domain) || "127.0.0.1".equals(domain)) {
			String message = "Partner registration is not supported for a localhost domain. This feature requires a publicly accessible domain name.";
			logger.warn(message);
			throw new TeslaApiException(message);
		}

		String partnerToken = getPartnerToken();
		if (partnerToken == null) {
			throw new TeslaApiException("Failed to register partner: could not obtain partner token.");
		}

		Map<String, String> failedRegions = new java.util.LinkedHashMap<>();

		for (String region : teslaApiConfig.getRegions().keySet()) {
			if (registeredRegionRepository.existsById(region)) {
				logger.debug("Skipping registration for already registered region: {}", region);
				continue;
			}
			String baseUrl = teslaApiConfig.getRegions().get(region);
			try {
				logger.info("Attempting to register domain '{}' with region '{}' at {}", domain, region, baseUrl);
				PartnerAccountApiResponse apiResponse = restClient.post()
						.uri(baseUrl + PARTNER_ACCOUNTS_PATH)
						.headers(headers -> headers.setBearerAuth(partnerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.body(Map.of("domain", domain))
						.retrieve()
						.body(PartnerAccountApiResponse.class);

				registeredRegionRepository.save(new RegisteredRegion(region));
				// Why: Logging the full response provides valuable details for verification and debugging.
				logger.info("Successfully registered with region: {}. Response: {}", region, apiResponse);
			}
			catch (Exception e) {
				logger.error("Failed to register with region: {}. Reason: {}", region, e.getMessage(), e);
				failedRegions.put(region, e.getMessage());
			}
		}

		if (!failedRegions.isEmpty()) {
			String failureDetails = failedRegions.entrySet().stream()
					.map(entry -> String.format("Region '%s' failed", entry.getKey()))
					.collect(java.util.stream.Collectors.joining(", "));
			throw new TeslaApiException("Partner registration failed for the following regions: " + failureDetails);
		}
	}

	public Map<String, String> validatePartnerRegistration() {
		String domain = teslaApiConfig.getDomain();
		if ("localhost".equalsIgnoreCase(domain) || "127.0.0.1".equals(domain)) {
			throw new TeslaApiException("Validation is not supported for a localhost domain. This feature requires a publicly accessible domain name.");
		}

		String localPublicKeyHex = getLocalPublicKeyAsHex();
		String partnerToken = getPartnerToken();
		Map<String, String> results = new LinkedHashMap<>();

		for (String region : teslaApiConfig.getRegions().keySet()) {
			String baseUrl = teslaApiConfig.getRegions().get(region);
			try {
				logger.debug("Validating public key for domain '{}' in region '{}'", domain, region);
				PublicKeyApiResponse apiResponse = restClient.get()
						.uri(baseUrl + PARTNER_PUBLIC_KEY_PATH + "?domain={domain}", domain)
						.headers(headers -> headers.setBearerAuth(partnerToken))
						.retrieve()
						.body(PublicKeyApiResponse.class);

				if (apiResponse != null && apiResponse.getResponse() != null && apiResponse.getResponse()
						.getPublicKey() != null) {
					String remotePublicKeyHex = apiResponse.getResponse().getPublicKey();
					if (localPublicKeyHex.equalsIgnoreCase(remotePublicKeyHex)) {
						results.put(region, "OK: Public keys match.");
					}
					else {
						results.put(region, "MISMATCH: Public keys do not match.");
						logger.warn("Public key mismatch for region {}. Local: [{}], Remote: [{}]", region, localPublicKeyHex, remotePublicKeyHex);
					}
				}
				else {
					results.put(region, "ERROR: Invalid or empty response from Tesla API.");
					logger.warn("Received invalid response when validating public key for region {}", region);
				}
			}
			catch (HttpClientErrorException.NotFound e) {
				results.put(region, "NOT FOUND: Domain is not registered in this region.");
				logger.warn("Domain '{}' not found in region '{}' during validation.", domain, region);
			}
			catch (Exception e) {
				logger.error("Failed to validate public key for region: {}", region, e);
				results.put(region, "ERROR: " + e.getMessage());
			}
		}
		return results;
	}

	private String getPartnerToken() {
		try {
			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
			params.add("grant_type", GRANT_TYPE_CLIENT_CREDENTIALS);
			params.add("client_id", teslaApiConfig.getClientId());
			params.add("client_secret", teslaApiConfig.getClientSecret());
			params.add("scope", teslaApiConfig.getScope());
			params.add("audience", teslaApiConfig.getAudience());

			TokenResponse tokenResponse = restClient.post()
					.uri(teslaApiConfig.getTokenUrl())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(params)
					.retrieve()
					.body(TokenResponse.class);

			return tokenResponse != null ? tokenResponse.getAccessToken() : null;
		}
		catch (Exception e) {
			throw new TeslaApiException("Failed to get partner token", e);
		}
	}

	private String getLocalPublicKeyAsHex() {
		PublicKey publicKey = keypairService.getPublicKey();
		if (!(publicKey instanceof ECPublicKey)) {
			throw new IllegalStateException("Public key is not an EC key, cannot format for validation.");
		}
		ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
		ECPoint ecPoint = ecPublicKey.getW();
		BigInteger x = ecPoint.getAffineX();
		BigInteger y = ecPoint.getAffineY();

		// The key size is derived from the curve parameters. For P-256, it's 32 bytes.
		int keySizeBytes = (ecPublicKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
		String hexFormat = "%0" + (keySizeBytes * 2) + "x";

		String xHex = String.format(hexFormat, x);
		String yHex = String.format(hexFormat, y);

		// Uncompressed format is 0x04 followed by x and y coordinates.
		return "04" + xHex + yHex;
	}
}
