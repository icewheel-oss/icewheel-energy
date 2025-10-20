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
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.modules.weather.WeatherService;
import net.icewheel.energy.infrastructure.modules.weather.dto.WeatherWidgetDTO;
import net.icewheel.energy.infrastructure.vendors.tesla.audit.model.TeslaAccountAuditEvent;
import net.icewheel.energy.infrastructure.vendors.tesla.audit.repository.TeslaAccountAuditEventRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaUserService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.shared.util.DateTimeUtil;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
@Slf4j
public class GlobalModelAttributeAdvice {

    private final TeslaUserService teslaUserService;
    private final TokenService tokenService;
    private final TeslaAccountAuditEventRepository auditEventRepository;
    private final WeatherService weatherService;

    @ModelAttribute("teslaConnected")
    public boolean addTeslaConnectionStatus(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return false;
        }

        User user = teslaUserService.findOrCreateUser(principal);

        // This method now correctly checks for a valid refresh token, which is the
        // best indicator of a persistent user connection for UI purposes.
        boolean hasValidToken = tokenService.isUserConnected(user);

        if (!hasValidToken) {
            return false;
        }

        Optional<TeslaAccountAuditEvent> lastEvent = auditEventRepository.findTopByUserOrderByTimestampDesc(user);

        if (lastEvent.isPresent() && lastEvent.get().getAction() == TeslaAccountAuditEvent.AuditAction.TESLA_ACCOUNT_DISCONNECT) {
            log.warn("User {} has a valid token but their last audit event was DISCONNECT. Treating as disconnected.", user.getId());
            return false;
        }

        return true;
    }

    @ModelAttribute("dateTimeUtil")
    public DateTimeUtil dateTimeUtil() {
        return new DateTimeUtil();
    }
	@ModelAttribute("userName")
	public String addUserName(@AuthenticationPrincipal OAuth2User principal) {
		if (principal == null) {
			return "Guest";
		}
		User user = teslaUserService.findOrCreateUser(principal);
		return user.getName();
	}

	/**
	 * Adds the user's timezone to the model.
	 * <p>
	 * This method retrieves the user's timezone from their preferences. If the user
	 * has not set a timezone preference, it falls back to the timezone provided by
	 * the OAuth2 provider. If that is also not available, it defaults to "UTC".
	 * This ensures that a valid timezone is always available in the model for
	 * rendering timestamps in a user-friendly way.
	 *
	 * @param principal The authenticated user principal.
	 * @return The user's timezone string.
	 */
	@ModelAttribute("userTimezone")
	public String addUserTimezone(@AuthenticationPrincipal OAuth2User principal) {
		if (principal == null) {
			return "UTC";
		}
		User user = teslaUserService.findOrCreateUser(principal);
		if (user.getPreference() != null && user.getPreference().getTimezone() != null) {
			return user.getPreference().getTimezone();
		}
		// Fallback to the timezone provided by the OAuth2 provider, if available.
		return principal.getAttribute("zoneinfo") != null ? principal.getAttribute("zoneinfo") : "UTC";
	}

	@ModelAttribute("weatherWidget")
	public WeatherWidgetDTO addWeatherWidgetData(@AuthenticationPrincipal OAuth2User principal) {
		if (principal == null) {
			return null;
		}
		User user = teslaUserService.findOrCreateUser(principal);
		if (user.getPreference() != null && user.getPreference().getZipCode() != null) {
			return weatherService.getWeatherWidgetData(user.getPreference().getZipCode()).orElse(null);
		} else {
			return new WeatherWidgetDTO(null, "Set Zip");
		}
	}
}
