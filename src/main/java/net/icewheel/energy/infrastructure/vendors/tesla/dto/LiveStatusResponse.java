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

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LiveStatusResponse {

    @JsonProperty("solar_power")
    private double solarPower;

    @JsonProperty("energy_left")
    private double energyLeft;

    @JsonProperty("total_pack_energy")
    private double totalPackEnergy;

    @JsonProperty("percentage_charged")
    private double percentageCharged;

    @JsonProperty("backup_capable")
    private boolean backupCapable;

    @JsonProperty("battery_power")
    private double batteryPower;

    @JsonProperty("load_power")
    private double loadPower;

    @JsonProperty("grid_status")
    private String gridStatus;

    @JsonProperty("grid_power")
    private double gridPower;

    @JsonProperty("island_status")
    private String islandStatus;

    @JsonProperty("storm_mode_active")
    private boolean stormModeActive;

    private Instant timestamp;
}
