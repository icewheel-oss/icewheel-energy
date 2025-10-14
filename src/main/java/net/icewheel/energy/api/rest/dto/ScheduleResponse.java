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

package net.icewheel.energy.api.rest.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import lombok.Data;
import net.icewheel.energy.application.scheduling.model.ReconciliationMode;
import net.icewheel.energy.application.scheduling.model.ScheduleType;

@Data
public class ScheduleResponse {
	private UUID scheduleGroupId;
	private String energySiteId;
	private String name;
	private String description;
	private Set<DayOfWeek> daysOfWeek;
	private LocalTime startTime;
	private LocalTime endTime;
	private ZoneId timeZone;
	private Integer onPeakBackupPercent;

	/**
	 * The effective off-peak backup percentage, which may be temporarily
	 * overridden by a weather-aware schedule.
	 */
	private Integer offPeakBackupPercent;

	/**
	 * The user's permanent, base on-peak backup percentage. This is used
	 * when populating the edit form so the user is always editing their base setting.
	 */
	private Integer permanentOnPeakBackupPercent;

	/**
	 * The user's permanent, base off-peak backup percentage. This is used
	 * when populating the edit form so the user is always editing their base setting.
	 */
	private Integer permanentOffPeakBackupPercent;

	private boolean enabled;
	private ReconciliationMode reconciliationMode;

	/**
	 * The type of the schedule (e.g., BASIC, WEATHER_AWARE).
	 */
	private ScheduleType scheduleType;

	/**
	 * The human-readable details from the last weather forecast evaluation.
	 */
	private String lastEvaluationDetails;

	private Instant createdAt;
	private Instant updatedAt;

	/**
	 * A flag indicating if the schedule's percentages are currently being
	 * overridden by a temporary weather-based schedule.
	 */
	private boolean overriddenByWeather;

	/**
	 * The user-configurable percentage (0-100) for weather-aware charging aggressiveness.
	 */
	private Integer weatherScalingFactor;
}