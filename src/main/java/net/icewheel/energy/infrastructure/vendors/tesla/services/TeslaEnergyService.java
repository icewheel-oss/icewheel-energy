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

package net.icewheel.energy.infrastructure.vendors.tesla.services;

import java.util.List;

import net.icewheel.energy.infrastructure.vendors.tesla.dto.ChargeHistoryResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.EnergyHistoryResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.LiveStatusResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.ProductsApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.SiteInfoResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.TelemetryHistoryResponse;

public interface TeslaEnergyService {

	SiteInfoResponse getSiteInfo(String userId, String siteId);

	LiveStatusResponse getLiveStatus(String userId, String siteId);

	EnergyHistoryResponse getEnergyHistory(String userId, String siteId, String period);

	List<Product> getProducts(String userId);

	List<Product> getSchedulableEnergySites(String userId);

	Boolean setBackupReserve(String userId, String siteId, int backupPercent);

	int getBackupReservePercent(String userId, String siteId);

	ProductsApiResponse getRawProducts(String userId);

	TelemetryHistoryResponse getTelemetryHistory(String userId, String siteId, String kind, String startDate, String endDate, String timeZone);

	ChargeHistoryResponse getChargeHistory(String userId, String siteId, String startDate, String endDate, String timeZone);

}
