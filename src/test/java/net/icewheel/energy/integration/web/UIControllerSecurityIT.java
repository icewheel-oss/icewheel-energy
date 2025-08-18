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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.icewheel.energy.api.web.viewmodel.TokenDetailView;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.Token;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.LiveStatusResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.services.TeslaEnergyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UIControllerSecurityIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@MockBean
	private TokenService tokenService;

	@MockBean
	private TeslaEnergyService teslaEnergyService;

	private User userA;
	private User userB;

	// Helper to create OAuth2 login principal per user when needed

	@BeforeEach
	void setup() {
		userRepository.deleteAll();

		userA = new User();
		userA.setId("sub-user-a");
		userA.setEmail("alice@example.com");
		userA.setName("Alice");
		userRepository.save(userA);

		userB = new User();
		userB.setId("sub-user-b");
		userB.setEmail("bob@example.com");
		userB.setName("Bob");
		userRepository.save(userB);

	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor oauthUser(User user) {
		java.util.Map<String, Object> attrs = new java.util.HashMap<>();
		attrs.put("sub", user.getId());
		attrs.put("email", user.getEmail());
		attrs.put("name", user.getName());
		attrs.put("zoneinfo", "UTC");
		java.util.Collection<org.springframework.security.core.GrantedAuthority> auth = java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
		org.springframework.security.oauth2.core.user.DefaultOAuth2User oAuth2User = new org.springframework.security.oauth2.core.user.DefaultOAuth2User(auth, attrs, "sub");
		org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken token = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(oAuth2User, auth, "test");
		return SecurityMockMvcRequestPostProcessors.authentication(token);
	}

	@Test
	void tokenDetails_isolated_per_user() throws Exception {
		// Why: The view template (`token-details.html`) expects `detail.token` and `detail.token.createdAt` to be non-null.
		// This test was failing with a NullPointerException because the `TeslaToken` object was not being set on the `TokenDetailView` model.
		// Creating and setting a token with a valid `createdAt` timestamp fixes the template rendering error.
		Token tokenA = new Token();
		// Why: The view template requires a complete Token object. We must set all fields accessed by the template
		// to prevent NullPointerExceptions during rendering. This includes the ID, all token strings, and expiration timestamps.
		tokenA.setId(UUID.randomUUID());
		// Why: The view template splits the access token to display its parts. Providing a non-null,
		// dot-separated string prevents a NullPointerException during template rendering.
		tokenA.setAccessToken("a.b.c");
		tokenA.setCreatedAt(Instant.now());
		tokenA.setRefreshToken("refresh-token-a-xyz");
		// Why: The view template formats the expiration timestamp. Providing a valid, non-zero value
		// prevents an error during template rendering.
		tokenA.setExpiration(Instant.now().plusSeconds(3600).getEpochSecond());
		tokenA.setRefreshTokenExpiration(Instant.now().plus(90, java.time.temporal.ChronoUnit.DAYS).getEpochSecond());
		Token tokenB = new Token();
		tokenB.setId(UUID.randomUUID());
		tokenB.setCreatedAt(Instant.now());
		tokenB.setAccessToken("x.y.z");
		tokenB.setRefreshToken("refresh-token-b-xyz");
		tokenB.setExpiration(Instant.now().plusSeconds(3600).getEpochSecond());
		tokenB.setRefreshTokenExpiration(Instant.now().plus(90, java.time.temporal.ChronoUnit.DAYS).getEpochSecond());

		// Prepare token details for each user
		TokenDetailView tdA = new TokenDetailView();
		tdA.setToken(tokenA);
		tdA.setScopes(List.of("scopeA"));
		tdA.setAccessTokenClaims(Map.of("aud", "A"));
		tdA.setAccessTokenPayloadJson("{\n  \"aud\": \"A\"\n}");
		TokenDetailView tdB = new TokenDetailView();
		tdB.setToken(tokenB);
		tdB.setScopes(List.of("scopeB"));
		tdB.setAccessTokenClaims(Map.of("aud", "B"));
		tdB.setAccessTokenPayloadJson("{\n  \"aud\": \"B\"\n}");

		when(tokenService.isUserConnected(any())).thenReturn(true);
		when(tokenService.getTokenDetailsForUser(eq(userA))).thenReturn(List.of(tdA));
		when(tokenService.getTokenDetailsForUser(eq(userB))).thenReturn(List.of(tdB));

		// Why: Add .with(csrf()) to ensure a CSRF token is available in the request before view rendering.
		// This prevents Thymeleaf from trying to create a session late in the lifecycle, which causes the "response committed" error.
		MvcResult resA = mockMvc.perform(get("/token-details").with(oauthUser(userA)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("token-details"))
				.andExpect(model().attributeExists("tokenDetails"))
				.andReturn();
		@SuppressWarnings("unchecked")
		List<TokenDetailView> modelA = (List<TokenDetailView>) resA.getModelAndView().getModel().get("tokenDetails");
		assertThat(modelA).extracting(v -> v.getScopes().getFirst()).containsExactly("scopeA");

		// Why: Add .with(csrf()) to ensure a CSRF token is available in the request before view rendering.
		MvcResult resB = mockMvc.perform(get("/token-details").with(oauthUser(userB)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("token-details"))
				.andExpect(model().attributeExists("tokenDetails"))
				.andReturn();
		@SuppressWarnings("unchecked")
		List<TokenDetailView> modelB = (List<TokenDetailView>) resB.getModelAndView().getModel().get("tokenDetails");
		assertThat(modelB).extracting(v -> v.getScopes().getFirst()).containsExactly("scopeB");
	}

	@Test
	void refreshAccessToken_calls_service_with_authenticated_user_only() throws Exception {
		UUID tokenId = UUID.randomUUID();

		mockMvc.perform(post("/refresh-access-token/" + tokenId).with(oauthUser(userA)).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(view().name("redirect:/token-details"));

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(tokenService, times(1)).forceRefreshToken(eq(tokenId), userCaptor.capture());
		assertThat(userCaptor.getValue().getEmail()).isEqualTo(userA.getEmail());
	}

	@Test
	void products_list_isolated_per_user() throws Exception {
		Product pA = new Product(); pA.setEnergySiteId("siteA"); pA.setResourceType("battery");
		pA.setSiteName("AliceSite");
		Product pB = new Product(); pB.setEnergySiteId("siteB"); pB.setResourceType("battery");
		pB.setSiteName("BobSite");

		when(tokenService.isUserConnected(any())).thenReturn(true);
		when(teslaEnergyService.getProducts(eq(userA.getId()))).thenReturn(List.of(pA));
		when(teslaEnergyService.getProducts(eq(userB.getId()))).thenReturn(List.of(pB));

		// Why: Add .with(csrf()) to ensure a CSRF token is available in the request before view rendering.
		// This prevents Thymeleaf from trying to create a session late in the lifecycle, which causes the "response committed" error.
		MvcResult resA = mockMvc.perform(get("/products").with(oauthUser(userA)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("products"))
				.andReturn();
		@SuppressWarnings("unchecked")
		List<Product> prodsA = (List<Product>) resA.getModelAndView().getModel().get("products");
		assertThat(prodsA).extracting(Product::getEnergySiteId).containsExactly("siteA");

		// Why: Add .with(csrf()) to ensure a CSRF token is available in the request before view rendering.
		MvcResult resB = mockMvc.perform(get("/products").with(oauthUser(userB)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("products"))
				.andReturn();
		@SuppressWarnings("unchecked")
		List<Product> prodsB = (List<Product>) resB.getModelAndView().getModel().get("products");
		assertThat(prodsB).extracting(Product::getEnergySiteId).containsExactly("siteB");
	}

	@Test
	void liveStatus_returns_only_for_owner_user() throws Exception {
		String siteId = "sharedSite";
		LiveStatusResponse ok = new LiveStatusResponse();
		when(tokenService.isUserConnected(any())).thenReturn(true);
		when(teslaEnergyService.getLiveStatus(eq(userA.getId()), eq(siteId))).thenReturn(ok);
		when(teslaEnergyService.getLiveStatus(eq(userB.getId()), eq(siteId))).thenReturn(null);

		mockMvc.perform(get("/api/live-status/" + siteId).with(oauthUser(userA)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/live-status/" + siteId).with(oauthUser(userB)))
				.andExpect(status().isServiceUnavailable());

		verify(teslaEnergyService, times(1)).getLiveStatus(eq(userA.getId()), eq(siteId));
		verify(teslaEnergyService, times(1)).getLiveStatus(eq(userB.getId()), eq(siteId));
	}
}
