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
import net.icewheel.energy.domain.energy.model.ReconciliationMode;

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
	private Integer offPeakBackupPercent;
	private boolean enabled;
	private ReconciliationMode reconciliationMode;
	private Instant createdAt;
	private Instant updatedAt;
}