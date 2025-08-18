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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * This service ensures that the Powerwall's state is correct when the application starts up.
 * Instead of trying to replay individual missed schedules (which can be complex and error-prone),
 * it triggers a full state reconciliation. This approach is more robust and acts as a self-healing
 * mechanism to correct any state drift that may have occurred during application downtime.
 * It leverages the same logic used for periodic state checks, unifying the application's
 * self-healing strategy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MisfireHandlingService {

	private final PowerwallStateReconciler stateReconciler;

	/**
	 * This method is the main entry point for handling potential misfires on application startup.
	 * It delegates directly to the PowerwallStateReconciler to ensure the state of all Powerwalls
	 * matches their active schedules.
	 */
	public void handleMisfiredSchedules() {
		log.info("Handling potential misfires by running a full state reconciliation...");
		stateReconciler.reconcileOnStartup();
		log.info("State reconciliation on startup complete.");
	}
}