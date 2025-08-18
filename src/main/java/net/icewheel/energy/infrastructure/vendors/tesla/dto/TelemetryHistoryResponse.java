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

package net.icewheel.energy.infrastructure.vendors.tesla.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelemetryHistoryResponse {
	private String period;
	@JsonProperty("time_series")
	private List<TimeSeries> timeSeries;

	@Data
	public static class TimeSeries {
		private OffsetDateTime timestamp;
		@JsonProperty("solar_energy_exported")
		private double solarEnergyExported;
		@JsonProperty("generator_energy_exported")
		private double generatorEnergyExported;
		@JsonProperty("grid_energy_imported")
		private double gridEnergyImported;
		@JsonProperty("grid_services_energy_imported")
		private double gridServicesEnergyImported;
		@JsonProperty("grid_services_energy_exported")
		private double gridServicesEnergyExported;
		@JsonProperty("grid_energy_exported_from_solar")
		private double gridEnergyExportedFromSolar;
		@JsonProperty("grid_energy_exported_from_generator")
		private double gridEnergyExportedFromGenerator;
		@JsonProperty("grid_energy_exported_from_battery")
		private double gridEnergyExportedFromBattery;
		@JsonProperty("battery_energy_exported")
		private double batteryEnergyExported;
		@JsonProperty("battery_energy_imported_from_grid")
		private double batteryEnergyImportedFromGrid;
		@JsonProperty("battery_energy_imported_from_solar")
		private double batteryEnergyImportedFromSolar;
		@JsonProperty("battery_energy_imported_from_generator")
		private double batteryEnergyImportedFromGenerator;
		@JsonProperty("consumer_energy_imported_from_grid")
		private double consumerEnergyImportedFromGrid;
		@JsonProperty("consumer_energy_imported_from_solar")
		private double consumerEnergyImportedFromSolar;
		@JsonProperty("consumer_energy_imported_from_battery")
		private double consumerEnergyImportedFromBattery;
		@JsonProperty("consumer_energy_imported_from_generator")
		private double consumerEnergyImportedFromGenerator;
	}
}