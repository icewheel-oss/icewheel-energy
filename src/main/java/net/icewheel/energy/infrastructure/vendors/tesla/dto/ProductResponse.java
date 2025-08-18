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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ProductResponse {
    private String id;
	private String vin;
	private String state;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("vehicle_id")
    private long vehicleId;
    @JsonProperty("access_type")
    private String accessType;
    @JsonProperty("display_name")
    private String displayName;
    @JsonProperty("in_service")
    private boolean inService;
    @JsonProperty("id_s")
    private String idS;
    @JsonProperty("calendar_enabled")
    private boolean calendarEnabled;
    @JsonProperty("api_version")
    private Integer apiVersion;
	@JsonProperty("command_signing")
	private String commandSigning;
    @JsonProperty("device_type")
    private String deviceType;
	@JsonProperty("mobile_access_disabled")
	private boolean mobileAccessDisabled;
	@JsonProperty("granular_access")
	private GranularAccess granularAccess;

	// Existing services fields
    @JsonProperty("energy_site_id")
    private String energySiteId;
    @JsonProperty("resource_type")
    private String resourceType;
    @JsonProperty("site_name")
    private String siteName;
    @JsonProperty("energy_left")
    private double energyLeft;
    @JsonProperty("total_pack_energy")
    private double totalPackEnergy;
    @JsonProperty("percentage_charged")
    private double percentageCharged;
    @JsonProperty("battery_power")
    private double batteryPower;
}