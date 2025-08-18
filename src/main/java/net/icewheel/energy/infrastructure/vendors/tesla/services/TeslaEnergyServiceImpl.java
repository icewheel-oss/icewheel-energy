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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import net.icewheel.energy.infrastructure.vendors.tesla.config.TeslaApiConfig;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.BackupRequest;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.ChargeHistoryApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.ChargeHistoryResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.EnergyHistoryApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.EnergyHistoryResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.LiveStatusApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.LiveStatusResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.Product;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.ProductResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.ProductsApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.SiteInfoApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.SiteInfoResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.TelemetryHistoryApiResponse;
import net.icewheel.energy.infrastructure.vendors.tesla.dto.TelemetryHistoryResponse;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeslaEnergyServiceImpl implements TeslaEnergyService {

	private final RestClient restClient;
	private final TeslaApiConfig teslaApiConfig;
	private final TokenService tokenService;

	/**
	 * A helper method to execute authenticated GET requests to the Tesla API.
	 * It centralizes access token retrieval and error handling.
	 *
	 * @param userId The user for whom the request is made.
	 * @param path The API endpoint path.
	 * @param responseType The expected response type class.
	 * @param uriVariables The variables to expand in the path.
	 * @return The API response body, or null if an error occurs.
	 * @param <T> The type of the response body.
	 */
	private <T> T executeGetRequest(String userId, String path, Class<T> responseType, Object... uriVariables) {
		String accessToken;
		try {
			accessToken = tokenService.getValidAccessToken(userId);
		}
		catch (IllegalStateException e) {
			log.error("Could not obtain a valid access token for user {}. Error: {}", userId, e.getMessage());
			return null;
		}

		String url = teslaApiConfig.getApiBaseUrl() + path;
		try {
			return restClient.get()
					.uri(url, uriVariables)
					.headers(headers -> headers.setBearerAuth(accessToken))
					.retrieve()
					.body(responseType);
		}
		catch (RestClientException e) {
			log.error("Error calling Tesla API at {} for user {}: {}", url, userId, e.getMessage());
			return null;
		}
	}

	@Override
	public SiteInfoResponse getSiteInfo(String userId, String siteId) {
		SiteInfoApiResponse apiResponse = executeGetRequest(userId, "/api/1/energy_sites/{siteId}/site_info", SiteInfoApiResponse.class, siteId);
		log.debug("Site info response for site {}: {}", siteId, apiResponse);
		// Return null on failure for consistency with getLiveStatus, making error handling more explicit.
		return (apiResponse != null && apiResponse.getResponse() != null) ? apiResponse.getResponse() : null;
	}

	@Override
	public LiveStatusResponse getLiveStatus(String userId, String siteId) {
		LiveStatusApiResponse apiResponse = executeGetRequest(userId, "/api/1/energy_sites/{siteId}/live_status", LiveStatusApiResponse.class, siteId);
		log.debug("Live status response for site {}: {}", siteId, apiResponse);
		// Return null if the API call fails or the response is empty, so callers can handle the failure.
		return (apiResponse != null && apiResponse.getResponse() != null) ? apiResponse.getResponse() : null;
	}

	@Override
	public EnergyHistoryResponse getEnergyHistory(String userId, String siteId, String period) {
		// Why: Using a wrapper class for the API response is more robust and consistent with other service calls.
		EnergyHistoryApiResponse apiResponse = executeGetRequest(userId, "/api/1/energy_sites/{siteId}/calendar_history?kind=energy&period={period}", EnergyHistoryApiResponse.class, siteId, period);
		return (apiResponse != null && apiResponse.getResponse() != null) ? apiResponse.getResponse() : new EnergyHistoryResponse();
	}

	@Override
	public List<Product> getProducts(String userId) {
		ProductsApiResponse apiResponse = executeGetRequest(userId, "/api/1/products", ProductsApiResponse.class);

		if (apiResponse == null || apiResponse.getResponse() == null) {
			log.warn("Received no products from Tesla API for user {}.", userId);
			return Collections.emptyList();
		}

		List<Product> finalProductList = new ArrayList<>();

		// Why: Partitioning the response allows us to handle vehicles and energy products
		// with separate, appropriate logic, making the process more robust.
		Map<Boolean, List<ProductResponse>> partitionedProducts = apiResponse.getResponse().stream()
				.filter(p -> p != null && p.getId() != null)
				.collect(Collectors.partitioningBy(p -> "vehicle".equals(p.getDeviceType())));

		List<ProductResponse> vehicles = partitionedProducts.get(true);
		List<ProductResponse> energyProducts = partitionedProducts.get(false);

		// Process vehicles by mapping them directly to the Product DTO.
		for (ProductResponse vehicleResponse : vehicles) {
			finalProductList.add(toProduct(vehicleResponse));
		}

		// Process energy products by grouping them by site and enriching with site-wide data.
		Map<String, List<ProductResponse>> productsBySiteId = energyProducts.stream()
				.filter(p -> p.getEnergySiteId() != null && !"0".equals(p.getEnergySiteId()))
				.filter(p -> "battery".equals(p.getResourceType()) || "wall_connector".equals(p.getResourceType()))
				.collect(Collectors.groupingBy(ProductResponse::getEnergySiteId));

		for (Map.Entry<String, List<ProductResponse>> entry : productsBySiteId.entrySet()) {
			String siteId = entry.getKey();
			List<ProductResponse> siteProducts = entry.getValue();

			SiteInfoResponse siteInfo = getSiteInfo(userId, siteId);
			LiveStatusResponse liveStatus = getLiveStatus(userId, siteId);

			for (ProductResponse productResponse : siteProducts) {
				Product product = toProduct(productResponse);
				if (siteInfo != null) {
					updateProductWithSiteInfo(product, siteInfo);
				}
				if (liveStatus != null) {
					updateProductWithLiveStatus(product, liveStatus);
				}
				finalProductList.add(product);
			}

			// If the site has solar, create a dedicated product representation for the UI.
			if (siteInfo != null && siteInfo.getComponents() != null && siteInfo.getComponents().isSolar()) {
				Product solarProduct = new Product();
				solarProduct.setEnergySiteId(siteId);
				solarProduct.setSiteName(siteInfo.getSiteName());
				solarProduct.setResourceType("solar");
				if (liveStatus != null) {
					solarProduct.setSolarPower(liveStatus.getSolarPower());
				}
				finalProductList.add(solarProduct);
			}
		}
		return finalProductList;
	}

	@Override
	public List<Product> getSchedulableEnergySites(String userId) {
		ProductsApiResponse apiResponse = getRawProducts(userId);

		if (apiResponse == null || apiResponse.getResponse() == null) {
			log.warn("Received no products from Tesla API for user {}.", userId);
			return Collections.emptyList();
		}

		// Why: This provides a clean, efficient list of only schedulable products (batteries)
		// to the UI, preventing deactivated or irrelevant products (like vehicles or chargers)
		// from appearing in the schedule creation form.
		return apiResponse.getResponse().stream()
				.filter(p -> p != null)
				.filter(p -> "battery".equals(p.getResourceType())) // Only batteries can have backup reserves scheduled.
				.filter(p -> p.getEnergySiteId() != null && !"0".equals(p.getEnergySiteId())) // Filter out invalid/deactivated sites.
				.map(this::toProduct) // Convert to our internal DTO.
				.collect(Collectors.toList());
	}

	@Override
	public ProductsApiResponse getRawProducts(String userId) {
		return executeGetRequest(userId, "/api/1/products", ProductsApiResponse.class);
	}

	@Override
	public TelemetryHistoryResponse getTelemetryHistory(String userId, String siteId, String kind, String startDate, String endDate, String timeZone) {
		String path = "/api/1/energy_sites/{siteId}/telemetry_history?kind={kind}&start_date={startDate}&end_date={endDate}&time_zone={timeZone}";
		TelemetryHistoryApiResponse apiResponse = executeGetRequest(userId, path, TelemetryHistoryApiResponse.class, siteId, kind, startDate, endDate, timeZone);
		// Return an empty object on failure to prevent template errors.
		return (apiResponse != null && apiResponse.getResponse() != null) ? apiResponse.getResponse() : new TelemetryHistoryResponse();
	}

	@Override
	public Boolean setBackupReserve(String userId, String siteId, int backupPercent) {
		String accessToken;
		try {
			accessToken = tokenService.getValidAccessToken(userId);
		}
		catch (IllegalStateException e) {
			log.error("Cannot set backup reserve for site {}: {}", siteId, e.getMessage());
			return false;
		}

		String url = teslaApiConfig.getApiBaseUrl() + "/api/1/energy_sites/{siteId}/backup";
		try {
			restClient.post()
					.uri(url, siteId)
					.headers(headers -> headers.setBearerAuth(accessToken))
					.body(new BackupRequest(backupPercent))
					.retrieve()
					.toBodilessEntity();
			log.info("Successfully set backup reserve to {}% for site {}", backupPercent, siteId);
			return true;
		}
		catch (RestClientException e) {
			log.error("Failed to set backup reserve to {}% for site {}. Error: {}", backupPercent, siteId, e.getMessage());
			return false;
		}
	}

	@Override
	public int getBackupReservePercent(String userId, String siteId) {
		SiteInfoResponse siteInfo = getSiteInfo(userId, siteId);
		if (siteInfo == null) {
			// Why: Throw an exception if site info cannot be retrieved, as this is an unexpected state for a reconciliation check.
			// The calling service is expected to handle this exception.
			throw new IllegalStateException("Could not retrieve site info for site " + siteId + " to check backup reserve.");
		}
		return siteInfo.getBackupReservePercent();
	}

	@Override
	public ChargeHistoryResponse getChargeHistory(String userId, String siteId, String startDate, String endDate, String timeZone) {
		String path = "/api/1/energy_sites/{siteId}/telemetry_history?kind=charge&start_date={startDate}&end_date={endDate}&time_zone={timeZone}";
		ChargeHistoryApiResponse apiResponse = executeGetRequest(userId, path, ChargeHistoryApiResponse.class, siteId, startDate, endDate, timeZone);
		return (apiResponse != null && apiResponse.getResponse() != null) ? apiResponse.getResponse() : new ChargeHistoryResponse();
	}

	/**
	 * Maps the raw API response object to the application's internal Product DTO.
	 */
	private Product toProduct(ProductResponse response) {
		Product product = new Product();
		product.setEnergySiteId(response.getEnergySiteId());
		product.setSiteName(response.getSiteName());
		product.setResourceType(response.getResourceType());
		product.setPercentageCharged(response.getPercentageCharged());
		product.setEnergyLeft(response.getEnergyLeft());
		product.setTotalPackEnergy(response.getTotalPackEnergy());
		product.setBatteryPower(response.getBatteryPower());

		// Why: Handle vehicle-specific data by checking the device_type. This ensures
		// that vehicle information is correctly mapped without interfering with energy products.
		if ("vehicle".equals(response.getDeviceType())) {
			product.setDisplayName(response.getDisplayName());
			product.setVin(response.getVin());
			product.setState(response.getState());
			product.setVehicleId(response.getVehicleId());
			// For vehicles, resource_type is often null, so we explicitly set it for the UI.
			product.setResourceType("vehicle");
		}
		return product;
	}

	/**
	 * Updates a Product object with fresh data from a LiveStatusResponse.
	 */
	private void updateProductWithLiveStatus(Product product, LiveStatusResponse liveStatus) {
		product.setPercentageCharged(liveStatus.getPercentageCharged());
		product.setBatteryPower(liveStatus.getBatteryPower());

		// Per user request, calculate energyLeft based on the total capacity and live percentage.
		// This ensures consistency between the displayed percentage and energy value.
		if (product.getTotalPackEnergy() > 0 && liveStatus.getPercentageCharged() > 0) {
			double energyLeft = product.getTotalPackEnergy() * (liveStatus.getPercentageCharged() / 100.0);
			product.setEnergyLeft(energyLeft);
		}
		else {
			// Fallback to the API value if total capacity wasn't available from site_info.
			product.setEnergyLeft(liveStatus.getEnergyLeft());
			// Also use totalPackEnergy from live_status as a fallback if not set from site_info.
			if (product.getTotalPackEnergy() <= 0) {
				product.setTotalPackEnergy(liveStatus.getTotalPackEnergy());
			}
		}
	}

	/**
	 * Updates a Product object with static data from a SiteInfoResponse.
	 */
	private void updateProductWithSiteInfo(Product product, SiteInfoResponse siteInfo) {
		product.setBatteryCount(siteInfo.getBatteryCount());
		product.setNameplatePower(siteInfo.getNameplatePower());
		// Per user request, calculate total capacity from site_info as it's more reliable for static data.
		// NOTE: The API field `nameplate_power` is assumed to be misnamed and actually represents
		// the energy capacity of a single battery in Wh.
		if (siteInfo.getBatteryCount() > 0 && siteInfo.getNameplatePower() > 0) {
			double totalEnergy = (double) siteInfo.getNameplatePower() * siteInfo.getBatteryCount();
			product.setTotalPackEnergy(totalEnergy);
		}
	}
}