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

package net.icewheel.energy.infrastructure.vendors.tesla.auth.controller;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.icewheel.energy.domain.audit.model.TeslaAccountAuditEvent;
import net.icewheel.energy.domain.audit.repository.TeslaAccountAuditEventRepository;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaAuthService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.UserService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.dto.TeslaTokenExchangeResult;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api/tesla/fleet/auth")
public class TeslaController {

    private final TeslaAuthService teslaAuthService;
    private final UserService userService;
	private final TokenService tokenService;
    private final TeslaAccountAuditEventRepository auditEventRepository;

	public TeslaController(TeslaAuthService teslaAuthService, UserService userService, TokenService tokenService, TeslaAccountAuditEventRepository auditEventRepository) {
        this.teslaAuthService = teslaAuthService;
        this.userService = userService;
		this.tokenService = tokenService;
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping("/url")
    public void getAuthUrl(@AuthenticationPrincipal OAuth2User principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (principal == null) {
            response.sendRedirect("/login");
            return;
        }

        String state = UUID.randomUUID().toString();
        request.getSession().setAttribute("state", state);
        String authURL = teslaAuthService.getAuthURL(state);
        response.sendRedirect(authURL);
    }

    @GetMapping("/callback")
    public void handleCallback(@RequestParam(name = "code", required = false) String code, @RequestParam("state") String state,
                               @RequestParam(name = "error", required = false) String error, @RequestParam(name = "error_description", required = false) String errorDescription, 
                               @AuthenticationPrincipal OAuth2User principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (principal == null) {
            response.sendRedirect("/login");
            return;
        }

        if (error != null) {
            String message = "Tesla login failed. Please try again.";
            if (errorDescription != null && !errorDescription.isEmpty()) {
                message += " Reason: " + errorDescription;
            }
            response.sendRedirect("/error?message=" + message);
            return;
        }

        String sessionState = (String) request.getSession().getAttribute("state");
        if (sessionState == null || !sessionState.equals(state)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid state parameter");
            return;
        }

        User user = userService.findOrCreateUser(principal);

		// The auth service exchanges the code, but the token service is responsible for saving it.
		TeslaTokenExchangeResult result = teslaAuthService.exchangeCodeForToken(code);
		tokenService.saveToken(user, result.tokenResponse(), result.userMeResponse());

        TeslaAccountAuditEvent event = new TeslaAccountAuditEvent();
        event.setUser(user);
        event.setAction(TeslaAccountAuditEvent.AuditAction.TESLA_ACCOUNT_CONNECT);
        auditEventRepository.save(event);

        response.sendRedirect("/");
    }

    @GetMapping("/disconnect")
    public void disconnect(@AuthenticationPrincipal OAuth2User principal, HttpServletResponse response) throws IOException {
        if (principal == null) {
            response.sendRedirect("/login");
            return;
        }

        User user = userService.findOrCreateUser(principal);
        userService.disconnectTeslaAccount(user);

        TeslaAccountAuditEvent event = new TeslaAccountAuditEvent();
        event.setUser(user);
        event.setAction(TeslaAccountAuditEvent.AuditAction.TESLA_ACCOUNT_DISCONNECT);
        auditEventRepository.save(event);

        response.sendRedirect("/");
    }
}
