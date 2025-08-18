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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SiteInfoResponse {

    private String id;

    @JsonProperty("site_name")
    private String siteName;

    @JsonProperty("backup_reserve_percent")
    private int backupReservePercent;

    @JsonProperty("default_real_mode")
    private String defaultRealMode;

    @JsonProperty("installation_date")
    private Instant installationDate;

    @JsonProperty("user_settings")
    private UserSettings userSettings;

    private Components components;
    private String version;

    @JsonProperty("battery_count")
    private int batteryCount;

    @JsonProperty("nameplate_power")
    private int nameplatePower;

    @JsonProperty("nameplate_energy")
    private int nameplateEnergy;

    @JsonProperty("installation_time_zone")
    private String installationTimeZone;

    @JsonProperty("max_site_meter_power_ac")
    private long maxSiteMeterPowerAc;

    @JsonProperty("min_site_meter_power_ac")
    private double minSiteMeterPowerAc;

	private String utility;

	@JsonProperty("vpp_backup_reserve_percent")
	private int vppBackupReservePercent;

	// Why: Explicitly mapping complex objects like tariffs provides type-safety and makes the data easier to use.
	@JsonProperty("tariff_content")
	private Map<String, Object> tariffContent;

	@JsonProperty("island_config")
	private IslandConfig islandConfig;

	// Why: This map captures any fields from the JSON response that are not explicitly defined
	// in this DTO. This makes the application resilient to API changes and allows for future use
	// of new data without breaking the application.
	@JsonIgnore
	private final Map<String, Object> additionalProperties = new HashMap<>();

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

    @Data
    public static class UserSettings {
        @JsonProperty("storm_mode_enabled")
        private boolean stormModeEnabled;

		@JsonProperty("off_grid_vehicle_charging_enabled")
		private boolean offGridVehicleChargingEnabled;

		@JsonProperty("powerwall_onboarding_settings_set")
		private boolean powerwallOnboardingSettingsSet;

		@JsonProperty("vpp_tour_enabled")
		private boolean vppTourEnabled;

		@JsonProperty("go_off_grid_test_banner_enabled")
		private boolean goOffGridTestBannerEnabled;

		@JsonProperty("powerwall_tesla_electric_interested_in")
		private boolean powerwallTeslaElectricInterestedIn;

		@JsonIgnore
		private final Map<String, Object> additionalProperties = new HashMap<>();

		@JsonAnyGetter
		public Map<String, Object> getAdditionalProperties() {
			return this.additionalProperties;
		}

		@JsonAnySetter
		public void setAdditionalProperty(String name, Object value) {
			this.additionalProperties.put(name, value);
		}
    }

    @Data
    public static class Components {
        private boolean solar;
        @JsonProperty("solar_type")
        private String solarType;
        private boolean battery;
        private boolean grid;
        private boolean backup;
		private String gateway;
		@JsonProperty("tou_capable")
		private boolean touCapable;
        @JsonProperty("load_meter")
        private boolean loadMeter;
        @JsonProperty("storm_mode_capable")
        private boolean stormModeCapable;
        @JsonProperty("off_grid_vehicle_charging_reserve_supported")
        private boolean offGridVehicleChargingReserveSupported;
        @JsonProperty("solar_value_enabled")
        private boolean solarValueEnabled;
		@JsonProperty("battery_solar_offset_view_enabled")
		private boolean batterySolarOffsetViewEnabled;
        @JsonProperty("set_islanding_mode_enabled")
        private boolean setIslandingModeEnabled;
		@JsonProperty("backup_time_remaining_enabled")
		private boolean backupTimeRemainingEnabled;
        @JsonProperty("battery_type")
        private String batteryType;
        private boolean configurable;
		@JsonProperty("grid_services_enabled")
		private boolean gridServicesEnabled;

		@JsonProperty("vehicle_charging_solar_offset_view_enabled")
		private boolean vehicleChargingSolarOffsetViewEnabled;

		@JsonProperty("vehicle_charging_performance_view_enabled")
		private boolean vehicleChargingPerformanceViewEnabled;

		@JsonProperty("system_alerts_enabled")
		private boolean systemAlertsEnabled;

		@JsonProperty("customer_preferred_export_rule")
		private String customerPreferredExportRule;

		@JsonProperty("edit_setting_permission_to_export")
		private boolean editSettingPermissionToExport;

		@JsonProperty("edit_setting_grid_charging")
		private boolean editSettingGridCharging;

		@JsonProperty("edit_setting_energy_exports")
		private boolean editSettingEnergyExports;

		@JsonProperty("service_mode_enabled")
		private boolean serviceModeEnabled;

		private List<Gateway> gateways;
		private List<Battery> batteries;

		@JsonIgnore
		private final Map<String, Object> additionalProperties = new HashMap<>();

		@JsonAnyGetter
		public Map<String, Object> getAdditionalProperties() {
			return this.additionalProperties;
		}

		@JsonAnySetter
		public void setAdditionalProperty(String name, Object value) {
			this.additionalProperties.put(name, value);
		}

		@Data
		public static class Gateway {
			@JsonProperty("device_id")
			private String deviceId;
			private String din;
			@JsonProperty("serial_number")
			private String serialNumber;
			@JsonProperty("part_name")
			private String partName;
			@JsonProperty("is_active")
			private boolean isActive;
			@JsonProperty("part_number")
			private String partNumber;
			@JsonProperty("firmware_version")
			private String firmwareVersion;
			@JsonProperty("updated_datetime")
			private Instant updatedDatetime;

			@JsonProperty("part_type")
			private int partType;

			@JsonProperty("site_id")
			private String siteId;

			@JsonIgnore
			private final Map<String, Object> additionalProperties = new HashMap<>();

			@JsonAnyGetter
			public Map<String, Object> getAdditionalProperties() {
				return this.additionalProperties;
			}

			@JsonAnySetter
			public void setAdditionalProperty(String name, Object value) {
				this.additionalProperties.put(name, value);
			}
		}

		@Data
		public static class Battery {
			@JsonProperty("device_id")
			private String deviceId;
			private String din;
			@JsonProperty("serial_number")
			private String serialNumber;
			@JsonProperty("part_name")
			private String partName;
			@JsonProperty("nameplate_energy")
			private int nameplateEnergy;
			@JsonProperty("is_active")
			private boolean isActive;
			@JsonProperty("part_number")
			private String partNumber;
			@JsonProperty("nameplate_max_charge_power")
			private int nameplateMaxChargePower;
			@JsonProperty("nameplate_max_discharge_power")
			private int nameplateMaxDischargePower;

			@JsonIgnore
			private final Map<String, Object> additionalProperties = new HashMap<>();

			@JsonAnyGetter
			public Map<String, Object> getAdditionalProperties() {
				return this.additionalProperties;
			}

			@JsonAnySetter
			public void setAdditionalProperty(String name, Object value) {
				this.additionalProperties.put(name, value);
			}
		}
	}

	@Data
	public static class IslandConfig {
		@JsonProperty("low_soe_limit")
		private int lowSoeLimit;

		@JsonProperty("jump_start_soe_threshold")
		private int jumpStartSoeThreshold;

		@JsonProperty("wait_for_solar_retry_soe")
		private int waitForSolarRetrySoe;

		@JsonProperty("max_frequency_shift_hz")
		private double maxFrequencyShiftHz;
    }
}
