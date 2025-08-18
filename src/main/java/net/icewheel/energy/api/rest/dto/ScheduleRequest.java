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
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.icewheel.energy.domain.energy.model.ReconciliationMode;

@Data
public class ScheduleRequest {

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private UUID id;

	// Why: This is no longer required from the client for imports, as it's determined
	// on the server. It is still required for create/update operations from the UI.
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@NotBlank(message = "Energy site must be selected for create or update operations.")
	private String energySiteId;

	@NotBlank(message = "Schedule name cannot be blank.")
	@Size(max = 100, message = "Schedule name cannot exceed 100 characters.")
	private String name;

	@Size(max = 255, message = "Description cannot exceed 255 characters.")
	private String description;

	@NotEmpty(message = "At least one day of the week must be selected.")
	private Set<DayOfWeek> daysOfWeek;

	@NotNull(message = "Start time is required.")
	private LocalTime startTime;

	@NotNull(message = "End time is required.")
	private LocalTime endTime;

	@NotBlank(message = "Time zone is required.")
	private String timeZone;

	@Min(value = 5, message = "Backup percentage must be at least 5%.")
	@Max(value = 80, message = "Backup percentage cannot exceed 80%.")
	private int onPeakBackupPercent;

	@Min(value = 5, message = "Backup percentage must be at least 5%.")
	@Max(value = 80, message = "Backup percentage cannot exceed 80%.")
	private int offPeakBackupPercent;

	private ReconciliationMode reconciliationMode;

	@NotNull
	private Boolean enabled;

}