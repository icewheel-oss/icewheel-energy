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

package net.icewheel.energy.api.admin;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.infrastructure.security.KeypairService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaAuthService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Serves pages related to developer and application-level information.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DeveloperPageController {

    private final KeypairService keypairService;
	private final TeslaAuthService teslaAuthService;

    @GetMapping("/developer/application-info")
    public String getApplicationInfoPage(Model model) {
        model.addAttribute("publicKeyPem", keypairService.getPublicKeyAsPem());
        model.addAttribute("activePage", "developer");
        return "application-info";
    }

	/**
	 * Manually triggers the partner registration process with Tesla's regional servers.
	 * This is useful for debugging connection or configuration issues.
	 */
	@PostMapping("/developer/register-partner")
	public String registerPartner(RedirectAttributes redirectAttributes) {
		try {
			teslaAuthService.registerPartner();
			redirectAttributes.addFlashAttribute("successMessage", "Successfully registered partner domain with all configured regions.");
		}
		catch (Exception e) {
			log.error("Manual partner registration failed", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Partner registration failed: " + e.getMessage());
		}
		return "redirect:/developer/application-info";
	}

	/**
	 * Manually triggers the validation of the application's public key against Tesla's records for each region.
	 */
	@PostMapping("/developer/validate-registration")
	public String validateRegistration(RedirectAttributes redirectAttributes) {
		try {
			Map<String, String> validationResults = teslaAuthService.validatePartnerRegistration();
			redirectAttributes.addFlashAttribute("validationResults", validationResults);
		}
		catch (Exception e) {
			log.error("Manual validation of partner registration failed", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Validation failed: " + e.getMessage());
		}
		return "redirect:/developer/application-info";
	}
}