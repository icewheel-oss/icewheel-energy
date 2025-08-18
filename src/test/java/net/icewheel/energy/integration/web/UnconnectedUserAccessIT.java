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
package net.icewheel.energy.integration.web;

import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UnconnectedUserAccessIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@MockBean
	private TokenService tokenService;

	private User user;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		when(tokenService.isUserConnected(any())).thenReturn(false);

		user = new User();
		user.setId("unconnected-id");
		user.setEmail("unconnected@example.com");
		user.setName("Unconnected User");
		userRepository.save(user);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor loginUser() {
		// Why: Using oauth2Login() is a more complete simulation of a user's session than just setting
		// the authentication token. It also sets up an OAuth2AuthorizedClient, which might be required
		// by parts of the security filter chain. This prevents the application from treating the
		// test user as unauthenticated, which would cause a 302 redirect to the login page.
		java.util.Map<String, Object> attrs = new java.util.HashMap<>();
		attrs.put("sub", user.getId());
		attrs.put("email", user.getEmail());
		attrs.put("name", user.getName());
		attrs.put("zoneinfo", "UTC");
		java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities =
				java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
		org.springframework.security.oauth2.core.user.DefaultOAuth2User oAuth2User =
				new org.springframework.security.oauth2.core.user.DefaultOAuth2User(authorities, attrs, "sub");
		return SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(oAuth2User);
	}

	@Test
	void unconnected_user_is_blocked_from_tesla_dependent_endpoints() throws Exception {
		// Why: UI pages that render forms (like the logout form in the header) require a CSRF token.
		// Adding .with(csrf()) prevents a 302 redirect that occurs when the token is missing in a test context.

		// Why: UI pages that require a Tesla connection should redirect to the home page with an error message.
		// This is handled by the GlobalWebExceptionHandler, which turns a 403 Forbidden exception into a 302 Redirect.
		mockMvc.perform(get("/products").with(loginUser()).with(csrf()))
				.andExpect(status().isFound()) // isFound() checks for 302
				.andExpect(redirectedUrl("/"));

		// Why: API endpoints are not handled by the GlobalWebExceptionHandler (which is scoped to @Controller),
		// so they should return the raw 403 Forbidden status.
		mockMvc.perform(get("/api/live-status/siteX").with(loginUser()))
				.andExpect(status().isForbidden());

		// Why: Schedule pages are UI pages and should also redirect.
		mockMvc.perform(get("/schedules").with(loginUser()).with(csrf()))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/"));
		mockMvc.perform(get("/schedules/history").with(loginUser()).with(csrf()))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/"));
		mockMvc.perform(get("/schedules/executions").with(loginUser()).with(csrf()))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/"));
	}
}
