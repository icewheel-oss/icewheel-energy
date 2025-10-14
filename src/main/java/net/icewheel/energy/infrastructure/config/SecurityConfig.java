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

package net.icewheel.energy.infrastructure.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enable method-level security for @PreAuthorize
public class SecurityConfig {

		@Bean
		public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
			http
					.headers(headers ->
							headers
									.frameOptions(withDefaults()) // Why: Default frame options are secure.
									.contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
											"script-src 'self' https://cdn.jsdelivr.net",
											"style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com",
											"font-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com",
											"object-src 'none'"
									)))
									.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
					)
					.authorizeHttpRequests(authorizeRequests ->
							authorizeRequests
									// Why: Use PathRequest to safely match static resources and keep basic pages public without over-broad URL patterns
									.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll() // css, js, images
									.requestMatchers("/login.html", "/license", "/terms", "/logout.html", "/", "/error", "/favicon.ico", "/robots.txt", "/weather/**")
									.permitAll()
									// Publicly accessible well-known endpoint for Tesla domain verification
									// Why: Required by Tesla Fleet API for domain verification; must be publicly accessible.
									.requestMatchers("/.well-known/appspecific/com.tesla.3p.public-key.pem").permitAll()
									// Permit selected actuator endpoints using EndpointRequest for safety
									// Why: Expose only non-sensitive actuator endpoints (health, info) publicly; others require authentication.
									.requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
									// Require auth for any other actuator endpoint
									// Why: Reduce attack surface by requiring authentication for all other actuator endpoints.
									.requestMatchers(EndpointRequest.toAnyEndpoint()).authenticated()
									// Secure all other requests
									.anyRequest().authenticated()
					)
					.oauth2Login(oauth2Login ->
							oauth2Login
									.loginPage("/login.html")
									.defaultSuccessUrl("/", true)
					)
					.logout(logout ->
							logout
									.logoutSuccessUrl("/logout.html")
									.invalidateHttpSession(true)
									.clearAuthentication(true)
									.deleteCookies("JSESSIONID")
					);
			return http.build();
		}
	}
