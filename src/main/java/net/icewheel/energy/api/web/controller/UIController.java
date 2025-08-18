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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.UserService;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.LiveStatusResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UIController {

    private final UserService userService;
    private final TokenService tokenService;
    private final TeslaEnergyService teslaEnergyService;

    private void addUserAttributesToModel(Model model, User user, OAuth2User oauth2User) {
        model.addAttribute("userName", user.getName());
        // Extract the user's timezone from the OAuth2 principal, defaulting to UTC.
        String timezone = oauth2User.getAttribute("zoneinfo") != null ? oauth2User.getAttribute("zoneinfo") : "UTC";
        model.addAttribute("timezone", timezone);
    }

    @GetMapping({"/", "/login.html"})
    public String index(Model model, @AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User != null) {
            return "redirect:/user-profile";
        }
        model.addAttribute("pageTitle", "Login");
        return "login";
    }

    @GetMapping("/logout.html")
    public String logoutPage(Model model) {
        model.addAttribute("pageTitle", "Logout");
        return "logout";
    }

    @GetMapping("/user-profile")
    public String userProfile(Model model, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = userService.findOrCreateUser(oauth2User);
        addUserAttributesToModel(model, user, oauth2User);
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("pageTitle", "Dashboard");
        return "user-profile";
    }

    @GetMapping("/token-details")
    public String tokenDetails(Model model, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = userService.findOrCreateUser(oauth2User);
        addUserAttributesToModel(model, user, oauth2User);
        model.addAttribute("activePage", "tokens");
        model.addAttribute("pageTitle", "API Token Details");

        // It's more robust to check the connection status directly via the service.
		boolean isTeslaConnected = tokenService.isUserConnected(user);
        model.addAttribute("teslaConnected", isTeslaConnected);

        if (isTeslaConnected) {
			model.addAttribute("tokenDetails", tokenService.getTokenDetailsForUser(user));
		}

        return "token-details";
    }

    @PostMapping("/refresh-access-token/{tokenId}")
    public String refreshAccessToken(@PathVariable UUID tokenId, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = userService.findOrCreateUser(oauth2User);
		tokenService.forceRefreshToken(tokenId, user);
        return "redirect:/token-details";
    }

    

    private void populateEnergyFlowData(Model model, User user) {
        List<Product> products = teslaEnergyService.getProducts(user.getId());
		// To display services flow, we need to find a 'battery' product, as it's the hub for site-wide data.
        // Relying on `products.getFirst()` is brittle if the first product is a Wall Connector.
        Optional<Product> batteryProduct = products.stream()
                .filter(p -> "battery".equals(p.getResourceType()))
                .findFirst();

        if (batteryProduct.isPresent()) {
            String siteId = batteryProduct.get().getEnergySiteId();
            model.addAttribute("siteId", siteId);
            model.addAttribute("siteInfo", teslaEnergyService.getSiteInfo(user.getId(), siteId));
            model.addAttribute("liveStatus", teslaEnergyService.getLiveStatus(user.getId(), siteId));
        } else {
			log.warn("User {} is connected but has no 'battery' type products to display services flow.", user.getId());
			// If no battery product is found, we can't show site-wide services flow.
            // The template will handle the absence of siteInfo and liveStatus.
        }
    }

    @GetMapping("/api/live-status/{siteId}")
    @ResponseBody
	public ResponseEntity<LiveStatusResponse> getLiveStatus(@PathVariable String siteId, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = userService.findOrCreateUser(oauth2User);
		// Block API access for users who are not connected to Tesla
		if (!tokenService.isUserConnected(user)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		LiveStatusResponse liveStatus = teslaEnergyService.getLiveStatus(user.getId(), siteId);
		// Return a proper response entity based on whether the call was successful.
		return liveStatus != null
				? ResponseEntity.ok(liveStatus)
				: ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @GetMapping("/products")
    public String products(Model model, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = userService.findOrCreateUser(oauth2User);
        addUserAttributesToModel(model, user, oauth2User);
        model.addAttribute("activePage", "products");
        model.addAttribute("pageTitle", "My Products");
		boolean isTeslaConnected = tokenService.isUserConnected(user);
		if (!isTeslaConnected) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tesla account not connected");
		}
		model.addAttribute("teslaConnected", true);
		model.addAttribute("products", teslaEnergyService.getProducts(user.getId()));
        return "products";
    }
}