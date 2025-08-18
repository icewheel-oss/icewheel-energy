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

package net.icewheel.energy.api.web.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.scheduling.PowerwallScheduleService;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.UserService;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SchedulePageController {

    private final PowerwallScheduleService scheduleService;
    private final UserService userService;
	private final TokenService tokenService;
	private final TeslaEnergyService teslaEnergyService;

    @GetMapping("/schedules")
    public String getSchedulesPage(Model model, @AuthenticationPrincipal OAuth2User principal) {
        User user = userService.findOrCreateUser(principal);
		if (!tokenService.isUserConnected(user)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tesla account not connected");
		}
        try {
			// Why: Use a more specific and efficient method to fetch only the products
			// that are relevant for scheduling (i.e., batteries). This prevents junk data
			// (like deactivated sites or non-battery products) from appearing in the UI
			// and improves page load performance by avoiding unnecessary API calls.
			model.addAttribute("products", teslaEnergyService.getSchedulableEnergySites(user.getId()));
        } catch (Exception e) {
			log.error("Could not fetch services products for user {}. The dropdown will be empty.", user.getId(), e);
        }
        model.addAttribute("activePage", "schedules");
        return "schedules";
    }

    @GetMapping("/schedules/history")
	public String getScheduleHistoryPage(Model model, @AuthenticationPrincipal OAuth2User principal,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
        User user = userService.findOrCreateUser(principal);
		if (!tokenService.isUserConnected(user)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tesla account not connected");
		}
		// Why: Use pagination to handle potentially large history logs efficiently, improving performance and user experience.
		Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
		model.addAttribute("historyPage", scheduleService.getScheduleHistory(user, pageable));
        model.addAttribute("activePage", "schedules");
        return "schedule-history";
    }

    @GetMapping("/schedules/executions")
	public String getScheduleExecutionHistoryPage(Model model, @AuthenticationPrincipal OAuth2User principal,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
        User user = userService.findOrCreateUser(principal);
		if (!tokenService.isUserConnected(user)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tesla account not connected");
		}
		// Why: Use pagination to handle potentially large execution logs efficiently, improving performance and user experience.
		// The service layer must be updated to accept a Pageable object.
		Pageable pageable = PageRequest.of(page, size, Sort.by("executionTime").descending());
		model.addAttribute("executionPage", scheduleService.getScheduleExecutionHistory(user, pageable));
        model.addAttribute("activePage", "schedules");
        return "schedule-execution-history";
    }
}