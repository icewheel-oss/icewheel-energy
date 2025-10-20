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

package net.icewheel.energy.api.rest.controller;

import lombok.RequiredArgsConstructor;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// Why: Explicitly produce JSON to enforce consistent API responses and avoid content negotiation issues.
@RequestMapping(value = "/api", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ApiController {

	private final TokenService tokenService;

    // Simple DTO for error responses
    record ApiError(String error) {}

    @GetMapping("/vehicles")
    public ResponseEntity<?> getVehicles(@AuthenticationPrincipal OAuth2User oauth2User) {
        String userId = oauth2User.getName();
		String accessToken = tokenService.getValidAccessToken(userId);
        if (accessToken != null) {
            // TODO: Call Tesla API to get vehicles and return a proper DTO
            return ResponseEntity.ok("Vehicles"); // Placeholder for actual vehicle DTO list
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                             .body(new ApiError("Valid Tesla API token not found for user."));
    }
}
