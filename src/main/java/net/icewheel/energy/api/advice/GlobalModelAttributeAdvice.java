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

package net.icewheel.energy.api.advice;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.domain.audit.model.TeslaAccountAuditEvent;
import net.icewheel.energy.domain.audit.repository.TeslaAccountAuditEventRepository;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.UserService;
import net.icewheel.energy.shared.util.DateTimeUtil;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
/**
 * A ControllerAdvice to add globally available attributes to the model for all controllers.
 * This ensures that common data needed by templates (like the header) is always present
 * without needing to be explicitly added in every controller method.
 */
// Why: This advice is targeted only at @Controller classes. This is a performance and security best practice,
// as it prevents these model attributes from being unnecessarily processed for @RestController API endpoints.
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
@Slf4j
public class GlobalModelAttributeAdvice {

    private final UserService userService;
	private final TokenService tokenService;
    private final TeslaAccountAuditEventRepository auditEventRepository;

    /**
     * Adds the 'teslaConnected' boolean flag to the model for every request.
     * The UI uses this flag to conditionally show or hide Tesla-specific navigation links.
     * A user is considered "connected" if the application can retrieve a valid access token for them.
     *
     * @param principal The currently authenticated user.
     * @return true if the user has a valid Tesla token, false otherwise.
     */
    @ModelAttribute("teslaConnected")
    public boolean addTeslaConnectionStatus(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return false;
        }

		User user = userService.findOrCreateUser(principal);

		// Check 1: A user must have a token that is not expired.
		// This is a lightweight check that doesn't trigger a network refresh.
		boolean hasValidToken = tokenService.isUserConnected(user);

        if (!hasValidToken) {
            return false;
        }

        // Check 2: To be considered connected, the last explicit action must not be a disconnect.
        // This prevents a state where a valid token might exist but the user has chosen to disconnect.
        Optional<TeslaAccountAuditEvent> lastEvent = auditEventRepository.findTopByUserOrderByTimestampDesc(user);

        if (lastEvent.isPresent() && lastEvent.get().getAction() == TeslaAccountAuditEvent.AuditAction.TESLA_ACCOUNT_DISCONNECT) {
            log.warn("User {} has a valid token but their last audit event was DISCONNECT. Treating as disconnected.", user.getId());
            return false;
        }

        // If they have a token and the last action wasn't a disconnect, they are connected.
        return true;
    }

	/**
	 * Adds a {@link DateTimeUtil} instance to the model for every request.
	 * This makes the utility available for formatting dates and times in all Thymeleaf templates.
	 */
	@ModelAttribute("dateTimeUtil")
	public DateTimeUtil dateTimeUtil() {
		return new DateTimeUtil();
	}
}