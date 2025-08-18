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

import java.util.List;
import java.util.UUID;

import net.icewheel.energy.api.web.viewmodel.TokenDetailView;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.Token;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.TokenResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.UserMeResponse;

/**
 * Service for managing Tesla OAuth tokens.
 */
public interface TokenService {

    /**
     * Saves a token to the database.
     * This is used for the initial creation of a token after user authorization.
     * @param user        the application user (from Google SSO)
     * @param tokenResponse the token response from the Tesla API
     * @param userMeResponse    the user details response from the Tesla API
     */
    void saveToken(User user, TokenResponse tokenResponse, UserMeResponse userMeResponse);

    /**
     * Creates a new token based on a refresh, preserving user details from the old token.
     * This supports token cycling where the old token is kept for a grace period.
     *
     * @param oldToken      the token that was used for the refresh
     * @param tokenResponse the new token response from the Tesla API
     */
    void saveToken(Token oldToken, TokenResponse tokenResponse);

    /**
	 * Retrieves a valid access token for the given user, refreshing it if necessary.
     *
	 * @param userId the ID of the user
	 * @return a valid access token
	 * @throws IllegalStateException if a token cannot be provided (e.g., user not found, no token, refresh failed)
     */
	String getValidAccessToken(String userId);

    /**
     * Revokes the token for the given user.
     *
     * @param user the user
     */
    void revokeToken(User user);

    /**
	 * Checks if a user has a token that is likely to be valid, without triggering a refresh.
	 * This is suitable for UI checks to determine if a user appears to be connected.
     *
     * @param user the user
	 * @return true if a non-expired token exists, false otherwise
	 */
	boolean isUserConnected(User user);

	/**
	 * Manually triggers a refresh for a specific token, checking ownership.
	 *
	 * @param tokenId the ID of the token to refresh
	 * @param user    the user attempting the refresh, for security validation
     */
	void forceRefreshToken(UUID tokenId, User user);

	/**
	 * Retrieves a detailed view of all tokens for a given user, including parsed JWT claims.
	 *
	 * @param user the user whose tokens are to be retrieved
	 * @return a list of {@link TokenDetailView} objects
	 */
	List<TokenDetailView> getTokenDetailsForUser(User user);
}
