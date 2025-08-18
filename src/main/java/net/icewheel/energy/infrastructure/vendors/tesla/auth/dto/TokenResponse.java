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

package net.icewheel.energy.infrastructure.vendors.tesla.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the response from Tesla's OAuth 2.0 token endpoint.
 * <p>
 * This class contains the access token, refresh token, and other relevant information.
 * It's important to understand the expiration policies for these tokens:
 * <ul>
 *     <li><b>Access Token:</b> Expires after 8 hours. The exact expiration timestamp is calculated and stored in the `expiration` field.</li>
 *     <li><b>Refresh Token:</b> Expires after 3 months and is single-use. When used to get a new access token, a new refresh token is also returned and must be saved for future use.</li>
 * </ul>
 */
@Setter
@Getter
public class TokenResponse {

    /**
     * The access token used to authenticate with the Tesla API.
     * This token has a limited lifetime and must be refreshed periodically.
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * The refresh token used to obtain a new access token. This token is single-use and expires after 3 months.
     * When this token is used, a new refresh token is issued and must be persisted for the next refresh.
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * A JSON Web Token (JWT) that contains user information.
     * This token is not typically used for API authentication but can be used for user identification.
     */
    @JsonProperty("id_token")
    private String idToken;

    /**
     * The type of token, which is typically "Bearer".
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * The number of seconds until the access token expires from the time it was issued.
     * This value is provided by the Tesla API.
     */
    @JsonProperty("expires_in")
    private int expiresIn;

    /**
     * The exact timestamp when the access token will expire, represented as the number of seconds since the Unix epoch.
     * This value is calculated locally by adding the `expires_in` value to the time the token was received.
     */
    private long expiration;

    @JsonProperty("scope")
    private String scope;

}
