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

package net.icewheel.energy.application.scheduling;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.repository.UserRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A scheduled task that proactively refreshes expiring Tesla API tokens for all users.
 * <p>
 * This scheduler runs periodically to ensure that API tokens remain valid for all services
 * that interact with the Tesla API. It iterates through all registered users and triggers
 * a token refresh if a user's token is nearing its expiration, based on a pre-configured
 * threshold.
 * <p>
 * The actual refresh logic, including the threshold check, is handled by the {@link TokenService}.
 * This class is simply the scheduled trigger for that process.
 *
 * NOTE: Ensure you add {@code @EnableScheduling} to your main application class to activate this scheduler.
 *
 * @see TokenService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshScheduler {

    private final UserRepository userRepository;
    private final TokenService tokenService;

    /**
	 * Runs periodically to check and refresh tokens that are nearing their expiration.
	 * The cron schedule is defined in `application.yml` under `app.token-refresh.cron`.
	 * ShedLock is used to ensure this task only runs on a single instance in a multi-node environment.
     */
	@Scheduled(cron = "${app.token-refresh.cron}")
	@SchedulerLock(name = "proactiveTokenRefresh", lockAtMostFor = "10m", lockAtLeastFor = "1m")
	public void proactivelyRefreshTokens() {
		log.info("Starting proactive token refresh job.");
        List<User> users = userRepository.findAll();

        for (User user : users) {
			try {
				// The getValidAccessToken method contains the logic to check for expiration (using the configured threshold)
				// and refresh if needed. Calling it is sufficient to trigger the refresh.
				tokenService.getValidAccessToken(user.getId());
			}
			catch (Exception e) {
				log.warn("Could not ensure valid token for user {}. Reason: {}", user.getId(), e.getMessage());
			}
		}
		log.info("Proactive token refresh job finished. Checked {} users.", users.size());
    }

}
