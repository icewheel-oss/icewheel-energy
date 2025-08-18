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

package net.icewheel.energy.api.rest.advice;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
public class AppErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model, @AuthenticationPrincipal OAuth2User principal) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        // Add attributes for the header fragment to prevent Thymeleaf errors
        if (principal != null) {
            model.addAttribute("userName", principal.getAttribute("name"));
            // Set a default active page for the error view, only needed when navbar is shown
            model.addAttribute("activePage", "error");
        }
        model.addAttribute("pageTitle", "Error");

        Integer statusCode = null;
        if (status != null) {
            try {
                statusCode = Integer.valueOf(status.toString());
            } catch (NumberFormatException e) {
                log.warn("Could not parse error status code: {}", status);
            }
        }

        // Use generic error message to avoid leaking internal details
        if (exception != null) {
            log.error("Request to {} resulted in an error:", request.getRequestURI(), exception);
        }
        // Why: Return a generic error message to avoid leaking internal exception details to end users.
        String errorMessage = statusCode != null
                ? "There was an error processing your request (Status: " + statusCode + ")."
                : "An unexpected error occurred.";

        model.addAttribute("statusCode", statusCode != null ? statusCode.toString() : "N/A");
        model.addAttribute("errorMessage", errorMessage);

        return "error"; // Renders src/main/resources/templates/error.html
    }
}
