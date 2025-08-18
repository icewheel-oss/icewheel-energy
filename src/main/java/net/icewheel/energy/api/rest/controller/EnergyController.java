
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.UserService;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.ChargeHistoryResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.EnergyHistoryResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.LiveStatusResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.SiteInfoResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.TelemetryHistoryResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for handling services-related requests.
 * This controller serves both API endpoints and web pages.
 */
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class EnergyController {

    /**
	 * Defines the valid periods for services history queries.
     */
    public enum Period {
        DAY,
        WEEK,
        MONTH,
        YEAR
    }

    private final TeslaEnergyService teslaEnergyService;
    private final UserService userService;
    private final TokenService tokenService;

    /**
     * Returns site information for the given site ID.
     *
     * @param oauth2User the authenticated user
     * @param siteId     the site ID
     * @return the site information
     */
    @GetMapping("/api/energy/sites/{siteId}/info")
    @ResponseBody
    public SiteInfoResponse getSiteInfo(@AuthenticationPrincipal OAuth2User oauth2User, @PathVariable String siteId) {
        String userId = oauth2User.getName();
        return teslaEnergyService.getSiteInfo(userId, siteId);
    }

    /**
     * Returns live status for the given site ID.
     *
     * @param oauth2User the authenticated user
     * @param siteId     the site ID
     * @return the live status
     */
    @GetMapping("/api/energy/sites/{siteId}/live_status")
    @ResponseBody
	public ResponseEntity<LiveStatusResponse> getLiveStatus(@AuthenticationPrincipal OAuth2User oauth2User, @PathVariable String siteId) {
        String userId = oauth2User.getName();
		LiveStatusResponse liveStatus = teslaEnergyService.getLiveStatus(userId, siteId);

		if (liveStatus != null) {
			return ResponseEntity.ok(liveStatus);
		}
		else {
			// Why: Return a specific error status so the client-side JS can handle it gracefully
			// instead of trying to parse a null/empty body as JSON, which would cause a crash.
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
    }

    /**
	 * Returns services history for the given site ID and period.
     *
     * @param oauth2User the authenticated user
     * @param siteId     the site ID
     * @param period     the period (day, week, month, year)
	 * @return the services history
     */
    @GetMapping("/api/energy/sites/{siteId}/history/{period}")
    @ResponseBody
    public EnergyHistoryResponse getEnergyHistory(@AuthenticationPrincipal OAuth2User oauth2User, @PathVariable String siteId, @PathVariable Period period) {
        String userId = oauth2User.getName();
        return teslaEnergyService.getEnergyHistory(userId, siteId, period.name().toUpperCase());
    }

    /**
	 * Serves the animated services flow page.
     *
     * @param model      the model
     * @param oauth2User the authenticated user
	 * @return the animated services flow page
     */
    @GetMapping("/energy-flow-animated")
    public String energyFlowAnimated(Model model, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = userService.findOrCreateUser(oauth2User);
		boolean isTeslaConnected = tokenService.isUserConnected(user);
        model.addAttribute("teslaConnected", isTeslaConnected);

        if (isTeslaConnected) {
			// Why: This logic is more robust. It specifically looks for a 'battery' product
			// to identify the primary services site, as the battery is the hub of the system.
			// It also correctly sources the user's timezone from the OAuth2 principal.
			populateEnergyFlowModel(model, user, oauth2User);
        }
        return "energy-flow-animated";
    }

    /**
	 * Serves the services flow page.
     *
     * @param model      the model
     * @param oauth2User the authenticated user
	 * @return the services flow page
     */
    @GetMapping("/energy-flow")
    public String energyFlow(Model model, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = userService.findOrCreateUser(oauth2User);
		boolean isTeslaConnected = tokenService.isUserConnected(user);
        model.addAttribute("teslaConnected", isTeslaConnected);

        if (isTeslaConnected) {
			populateEnergyFlowModel(model, user, oauth2User);
		}
		return "energy-flow";
	}

	private void populateEnergyFlowModel(Model model, User user, OAuth2User oauth2User) {
		List<Product> products = teslaEnergyService.getProducts(user.getId());
		Optional<Product> batteryProductOpt = products.stream()
				.filter(p -> "battery".equals(p.getResourceType()))
				.findFirst();

		if (batteryProductOpt.isPresent()) {
			Product batteryProduct = batteryProductOpt.get();
			String siteId = batteryProduct.getEnergySiteId();
			String timezone = oauth2User.getAttribute("zoneinfo") != null ? oauth2User.getAttribute("zoneinfo") : "UTC";

			model.addAttribute("siteId", siteId);
			model.addAttribute("timezone", timezone);
			model.addAttribute("batteryProduct", batteryProduct); // Pass the enriched product
			model.addAttribute("liveStatus", teslaEnergyService.getLiveStatus(user.getId(), siteId));
			model.addAttribute("siteInfo", teslaEnergyService.getSiteInfo(user.getId(), siteId));
		}
		else {
			model.addAttribute("errorMessage", "A Powerwall product is required to display services flow.");
		}
    }

	/**
	 * Serves a page with detailed information about a specific services site.
	 * @param siteId The ID of the site to display.
	 * @param model The Spring model.
	 * @param oauth2User The authenticated user.
	 * @return The site details view.
	 */
	@GetMapping("/sites/{siteId}")
	public String siteDetails(@PathVariable String siteId, Model model, @AuthenticationPrincipal OAuth2User oauth2User) {
		User user = userService.findOrCreateUser(oauth2User);

		// Why: Consolidating all UI model attributes here makes this the single source of truth for this page.
		model.addAttribute("userName", user.getName());
		String timezone = oauth2User.getAttribute("zoneinfo") != null ? oauth2User.getAttribute("zoneinfo") : "UTC";
		model.addAttribute("timezone", timezone);
		model.addAttribute("activePage", "products"); // Site details are part of the products section
		model.addAttribute("pageTitle", "Site Details");

		boolean isTeslaConnected = tokenService.isUserConnected(user);
		model.addAttribute("teslaConnected", isTeslaConnected);

		if (isTeslaConnected) {
			// Why: Moving the data population logic directly into the controller method
			// makes the data flow for this specific page more explicit and easier to debug,
			// ensuring the raw products response is always handled correctly.
			var productsResponse = teslaEnergyService.getRawProducts(user.getId());
			model.addAttribute("productsResponse", productsResponse);

			boolean isValidSite = productsResponse != null && productsResponse.getResponse() != null &&
					productsResponse.getResponse().stream()
							.filter(p -> p != null && siteId.equals(p.getEnergySiteId()))
							.anyMatch(p -> "battery".equals(p.getResourceType()) || "wall_connector".equals(p.getResourceType()));

			if (isValidSite) {
				SiteInfoResponse siteInfo = teslaEnergyService.getSiteInfo(user.getId(), siteId);
				model.addAttribute("siteInfo", siteInfo);
				if (siteInfo != null) { // fetchLiveStatus is true for this page
					model.addAttribute("liveStatus", teslaEnergyService.getLiveStatus(user.getId(), siteId));
				}
			}
			else {
				model.addAttribute("siteInfo", null);
			}
		}
		return "site-details";
	}

	/**
	 * Serves a page with detailed telemetry history for a specific services site.
	 * @param siteId The ID of the site to display.
	 * @param kind The kind of data to retrieve (e.g., 'charge').
	 * @param startDate The start date for the data window.
	 * @param endDate The end date for the data window.
	 * @param model The Spring model.
	 * @param oauth2User The authenticated user.
	 * @return The telemetry history view.
	 */
	@GetMapping("/sites/{siteId}/telemetry")
	public String telemetryHistoryPage(
			@PathVariable String siteId,
			@RequestParam(required = false, defaultValue = "charge") String kind,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Model model,
			@AuthenticationPrincipal OAuth2User oauth2User) {

		User user = userService.findOrCreateUser(oauth2User);
		boolean isTeslaConnected = tokenService.isUserConnected(user);
		model.addAttribute("teslaConnected", isTeslaConnected);
		model.addAttribute("siteId", siteId);

		if (isTeslaConnected) {
			SiteInfoResponse siteInfo = populateSiteModel(user.getId(), siteId, model, false);

			model.addAttribute("kind", kind);

			// Only fetch history if the form has been submitted (i.e., startDate is not null) and the site is valid.
			if (startDate != null && siteInfo != null) {
				LocalDate finalEndDate = (endDate == null) ? startDate : endDate;
				model.addAttribute("startDate", startDate);
				model.addAttribute("endDate", finalEndDate);

				ZoneId zoneId = ZoneId.of(siteInfo.getInstallationTimeZone());
				OffsetDateTime startDateTime = startDate.atStartOfDay(zoneId).toOffsetDateTime();
				OffsetDateTime endDateTime = finalEndDate.atTime(LocalTime.MAX).atZone(zoneId).toOffsetDateTime();

				TelemetryHistoryResponse history = teslaEnergyService.getTelemetryHistory(user.getId(), siteId, kind, startDateTime.toString(), endDateTime.toString(), siteInfo.getInstallationTimeZone());
				model.addAttribute("history", history);
			}
		}
		return "telemetry-history";
	}

	/**
	 * Serves a page with aggregated services history for a specific services site.
	 * @param siteId The ID of the site to display.
	 * @param period The aggregation period (e.g., 'day', 'week').
	 * @param model The Spring model.
	 * @param oauth2User The authenticated user.
	 * @return The services history view.
	 */
	@GetMapping("/sites/{siteId}/energy-history")
	public String energyHistoryPage(
			@PathVariable String siteId,
			@RequestParam(required = false) String period,
			Model model,
			@AuthenticationPrincipal OAuth2User oauth2User) {

		User user = userService.findOrCreateUser(oauth2User);
		boolean isTeslaConnected = tokenService.isUserConnected(user);
		model.addAttribute("teslaConnected", isTeslaConnected);
		model.addAttribute("siteId", siteId);

		if (isTeslaConnected) {
			SiteInfoResponse siteInfo = populateSiteModel(user.getId(), siteId, model, false);

			// Only fetch data if the user has submitted the form (period is not null) and the site is valid.
			if (period != null && siteInfo != null) {
				model.addAttribute("period", period.toLowerCase());
				EnergyHistoryResponse history = teslaEnergyService.getEnergyHistory(user.getId(), siteId, period.toUpperCase());
				model.addAttribute("history", history);
			}
			else {
				// For the first page load, just set a default for the dropdown.
				model.addAttribute("period", "day");
			}
		}
		return "energy-history";
	}

	/**
	 * Serves a page with charge history for a specific services site.
	 * @param siteId The ID of the site to display.
	 * @param startDate The start date for the data window.
	 * @param endDate The end date for the data window.
	 * @param model The Spring model.
	 * @param oauth2User The authenticated user.
	 * @return The charge history view.
	 */
	@GetMapping("/sites/{siteId}/charge-history")
	public String chargeHistoryPage(
			@PathVariable String siteId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Model model,
			@AuthenticationPrincipal OAuth2User oauth2User) {

		User user = userService.findOrCreateUser(oauth2User);
		boolean isTeslaConnected = tokenService.isUserConnected(user);
		model.addAttribute("teslaConnected", isTeslaConnected);
		model.addAttribute("siteId", siteId);

		if (isTeslaConnected) {
			SiteInfoResponse siteInfo = populateSiteModel(user.getId(), siteId, model, false);

			if (startDate != null && siteInfo != null) {
				LocalDate finalEndDate = (endDate == null) ? startDate : endDate;
				model.addAttribute("startDate", startDate);
				model.addAttribute("endDate", finalEndDate);

				ZoneId zoneId = ZoneId.of(siteInfo.getInstallationTimeZone());
				OffsetDateTime startDateTime = startDate.atStartOfDay(zoneId).toOffsetDateTime();
				OffsetDateTime endDateTime = finalEndDate.atTime(LocalTime.MAX).atZone(zoneId).toOffsetDateTime();

				ChargeHistoryResponse history = teslaEnergyService.getChargeHistory(user.getId(), siteId, startDateTime.toString(), endDateTime.toString(), siteInfo.getInstallationTimeZone());
				model.addAttribute("history", history);
			}
		}
		return "charge-history";
	}

	/**
	 * A robust helper method to populate the model for any page related to a specific site.
	 * It validates the site ID against the user's products, and if valid, fetches and adds
	 * the necessary site information to the model. It always adds the raw products response
	 * for potential debugging use on any page.
	 *
	 * @param fetchLiveStatus If true, also fetches and adds the `live_status` data.
	 * @return The SiteInfoResponse if valid, otherwise null.
	 */
	private SiteInfoResponse populateSiteModel(String userId, String siteId, Model model, boolean fetchLiveStatus) {
		var productsResponse = teslaEnergyService.getRawProducts(userId);
		model.addAttribute("productsResponse", productsResponse);

		boolean isValidSite = productsResponse != null && productsResponse.getResponse() != null &&
				productsResponse.getResponse().stream()
						.filter(p -> p != null && siteId.equals(p.getEnergySiteId()))
						.anyMatch(p -> "battery".equals(p.getResourceType()) || "wall_connector".equals(p.getResourceType()));

		if (isValidSite) {
			SiteInfoResponse siteInfo = teslaEnergyService.getSiteInfo(userId, siteId);
			model.addAttribute("siteInfo", siteInfo);
			if (siteInfo != null && fetchLiveStatus) {
				model.addAttribute("liveStatus", teslaEnergyService.getLiveStatus(userId, siteId));
			}
			return siteInfo;
		}

		model.addAttribute("siteInfo", null);
		return null;
	}
}
