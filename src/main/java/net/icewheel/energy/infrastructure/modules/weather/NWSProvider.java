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

package net.icewheel.energy.infrastructure.modules.weather;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.infrastructure.modules.weather.dto.RawForecastPayload;
import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderType;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.ObservationStationCollectionJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.PointJsonLd;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestClient;

@Slf4j
public class NWSProvider implements WeatherProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NWSProvider(@Qualifier("nwsRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeatherProviderType getType() {
        return WeatherProviderType.NWS;
    }

    /**
     * Fetches all raw forecast payloads from the NWS API for a given location.
     */
    @Override
    public RawForecastPayload fetchRawForecastPayload(@NonNull Location location) {
        PointsResponseData pointsData = getForecastUrlsAndRawResponse(location);
        Map<String, String> forecastUrls = pointsData.urls();
        String rawPointsResponse = pointsData.rawResponse();

		// First, retrieve the raw JSON response as a string.
		// This allows us to log it for debugging and store it for auditing.
		String rawHourlyJsonResponse = restClient.get()
                .uri(forecastUrls.get("hourly"))
				.accept(new MediaType("application", "ld+json"))
                .retrieve()
				.body(String.class);

		log.debug("NWS raw hourly forecast response: {}", rawHourlyJsonResponse);

		String rawDailyJsonResponse = restClient.get()
				.uri(forecastUrls.get("daily"))
				.accept(new MediaType("application", "ld+json"))
				.retrieve()
				.body(String.class);

		log.debug("NWS raw daily forecast response: {}", rawDailyJsonResponse);

		String rawGridDataJsonResponse = restClient.get()
				.uri(forecastUrls.get("gridData"))
				.accept(new MediaType("application", "ld+json"))
				.retrieve()
				.body(String.class);

		log.debug("NWS raw grid data forecast response: {}", rawGridDataJsonResponse);

		String rawObservationStationsResponse = restClient.get()
				.uri(forecastUrls.get("observationStations"))
				.accept(new MediaType("application", "ld+json"))
				.retrieve()
				.body(String.class);

		log.debug("NWS raw observation stations response: {}", rawObservationStationsResponse);

		ObservationStationCollectionJsonLd observationStations = fromJson(rawObservationStationsResponse, ObservationStationCollectionJsonLd.class);

		String rawObservationJson = "";
		if (observationStations != null && observationStations.getObservationStations() != null && !observationStations.getObservationStations().isEmpty()) {
			String latestObservationUrl = observationStations.getObservationStations().getFirst().toString() + "/observations/latest";
			rawObservationJson = restClient.get()
					.uri(latestObservationUrl)
					.accept(new MediaType("application", "ld+json"))
					.retrieve()
					.body(String.class);
		}

		log.debug("NWS raw observation response: {}", rawObservationJson);


        return new RawForecastPayload(rawPointsResponse, rawHourlyJsonResponse, rawDailyJsonResponse, rawGridDataJsonResponse, rawObservationStationsResponse, rawObservationJson);
    }

    private record PointsResponseData(Map<String, String> urls, String rawResponse) {}

    private PointsResponseData getForecastUrlsAndRawResponse(Location location) {
        var pointsUri = "/points/{latitude},{longitude}";

        String rawPointsResponse = restClient.get()
				.uri(pointsUri, location.getLatitude(), location.getLongitude())
				.accept(new MediaType("application", "ld+json"))
				.retrieve()
				.body(String.class);

        log.debug("NWS raw points response: {}", rawPointsResponse);

        PointJsonLd pointsResponse = fromJson(rawPointsResponse, PointJsonLd.class);

        String hourlyUrl = Optional.ofNullable(pointsResponse)
                .map(PointJsonLd::getForecastHourly)
                .map(URI::toString)
                .orElseThrow(() -> new ForecastLinkNotFoundException("Could not find 'forecastHourly' link in NWS response"));

		String dailyUrl = Optional.ofNullable(pointsResponse)
				.map(PointJsonLd::getForecast)
				.map(URI::toString)
				.orElseThrow(() -> new ForecastLinkNotFoundException("Could not find 'forecast' link in NWS response"));

		String gridDataUrl = Optional.ofNullable(pointsResponse)
				.map(PointJsonLd::getForecastGridData)
				.map(URI::toString)
				.orElseThrow(() -> new ForecastLinkNotFoundException("Could not find 'forecastGridData' link in NWS response"));

		String observationStationsUrl = Optional.ofNullable(pointsResponse)
				.map(PointJsonLd::getObservationStations)
				.map(URI::toString)
				.orElseThrow(() -> new ForecastLinkNotFoundException("Could not find 'observationStations' link in NWS response"));

        Map<String, String> urls = Map.of("hourly", hourlyUrl, "daily", dailyUrl, "gridData", gridDataUrl, "observationStations", observationStationsUrl);
        return new PointsResponseData(urls, rawPointsResponse);
    }

	/**
	 * Deserializes the given JSON string to an object of the specified class.
	 *
	 * @param json The JSON string to deserialize.
	 * @param clazz The class to deserialize the JSON into.
	 * @return An instance of the specified class.
	 */
	private <T> T fromJson(String json, Class<T> clazz) {
		try {
			return objectMapper.readValue(json, clazz);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to deserialize forecast from JSON", e);
		}
	}

}