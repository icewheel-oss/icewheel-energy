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

import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaAuthService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for automatically registering our application as a "partner" with Tesla.
 * This is a background task that helps set up the necessary connection between our service and Tesla's systems.
 * <p>
 * It's specifically designed to run only in a production environment (`@Profile("prod")`)
 * because partner registration requires a publicly accessible domain, which is typically not available
 * during local development or testing.
 * </p>
 */
@Component
// Why: The partner registration scheduler should only run in a production-like environment
// where the application has a publicly accessible domain. It is tied to the 'prod' profile
// to prevent it from running during local development with other profiles.
@Profile("prod")
public class PartnerRegistrationScheduler {

    private final TeslaAuthService teslaAuthService;

    public PartnerRegistrationScheduler(TeslaAuthService teslaAuthService) {
        this.teslaAuthService = teslaAuthService;
    }

	/**
	 * This method is scheduled to run automatically once a day.
	 * Its primary purpose is to trigger the partner registration process with Tesla.
	 * <p>
	 * It uses a "scheduler lock" to ensure that even if multiple instances of our application
	 * are running, only one of them will attempt the partner registration at any given time,
	 * preventing conflicts or unnecessary repeated calls.
	 * </p>
	 */
    @Scheduled(cron = "0 0 0 * * ?") // Run once a day
    // Why: Prevent duplicate partner registrations across instances; ShedLock ensures single execution.
    @SchedulerLock(name = "registerPartner", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void schedulePartnerRegistration() {
        teslaAuthService.registerPartner();
    }
}
