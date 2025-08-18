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

import lombok.Data;

/**
 * A simplified view model representing a user's Tesla product, such as a Powerwall or Wall Connector.
 * This is populated from the more detailed ProductResponse from the Tesla API.
 */
@Data
public class Product {
	private String energySiteId;
	private String siteName;
	private String resourceType;
	private double percentageCharged;
	private double energyLeft;
	private double totalPackEnergy;
	private double batteryPower;

	// Fields populated from SiteInfo and LiveStatus for a complete view
	private double solarPower;
	private int batteryCount;
	private int nameplatePower; // in Watts

	// New fields for vehicles
	private String vin;
	private String state;
	private String displayName;
	private long vehicleId;
}