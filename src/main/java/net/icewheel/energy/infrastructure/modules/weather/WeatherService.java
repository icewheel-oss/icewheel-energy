package net.icewheel.energy.infrastructure.modules.weather;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.modules.location.zip.model.ZipCodeCache;
import net.icewheel.energy.infrastructure.modules.location.zip.service.ZipCodeService;
import net.icewheel.energy.infrastructure.modules.weather.dto.ProcessedForecast;
import net.icewheel.energy.infrastructure.modules.weather.dto.RawForecastPayload;
import net.icewheel.energy.infrastructure.modules.weather.dto.WeatherPageData;
import net.icewheel.energy.infrastructure.modules.weather.dto.WeatherWidgetDTO;
import net.icewheel.energy.infrastructure.modules.weather.dto.WeatherWidgetPayload;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.Gridpoint;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.Gridpoint12hForecastJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.PointJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.QuantitativeValue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A service responsible for orchestrating the fetching, processing, and storage of weather data.
 * It acts as a central point for all weather-related operations, interacting with the
 * {@link WeatherProvider} to get external data and with repositories to persist it.
 * It does not use in-memory caching, relying on the database as the source of truth.
 *
 * <h2>Timezone Handling</h2>
 * The application handles timezones as follows:
 * <ul>
 *     <li>The user's preferred zip code is stored in their profile.</li>
 *     <li>When the user visits the weather page, the application fetches the weather for the user's preferred zip code.</li>
 *     <li>The NWS API returns the timezone for the location of the zip code.</li>
 *     <li>The frontend then uses this timezone to display the weather data in the correct local time.</li>
 * </ul>
 *
 * If a user travels to a different timezone, the weather page will continue to display the weather
 * for the zip code stored in their preferences, not their current location. To view the weather for their
 * current location, they would need to manually enter the zip code.
 */
@Service
@Slf4j
public class WeatherService {

	private final WeatherProvider weatherProvider;
	private final ForecastReportRepository forecastReportRepository;
	private final LocationRepository locationRepository;
	private final ZipCodeService zipCodeService;
	private final ObjectMapper objectMapper;

	/**
	 * Constructs a new WeatherService with the necessary dependencies.
	 *
	 * @param weatherProvider The provider for fetching external weather data (e.g., NWS).
	 * @param forecastReportRepository The repository for storing raw forecast reports.
	 * @param locationRepository The repository for managing geographic locations.
	 * @param zipCodeService The service for converting zip codes to coordinates.
	 * @param objectMapper The Jackson ObjectMapper for JSON processing.
	 */
	public WeatherService(
			@Qualifier("nwsProvider") WeatherProvider weatherProvider,
			ForecastReportRepository forecastReportRepository,
			LocationRepository locationRepository,
			ZipCodeService zipCodeService,
			ObjectMapper objectMapper) {
		this.weatherProvider = weatherProvider;
		this.forecastReportRepository = forecastReportRepository;
		this.locationRepository = locationRepository;
		this.zipCodeService = zipCodeService;
		this.objectMapper = objectMapper;
	}

	/**
	 * Retrieves a forecast report from the database for a given latitude and longitude.
	 * If no fresh report is found, it triggers a new fetch from the external provider.
	 * This method is the primary uncached entry point for getting a forecast report.
	 *
	 * @param latitude  the latitude
	 * @param longitude the longitude
	 * @return an {@link Optional} containing the {@link ForecastReport}, or empty if an error occurred.
	 */
	// This method is the uncached entry point to retrieve a forecast report and should be private.
	@Transactional
	Optional<ForecastReport> getForecastReportFromDb(double latitude, double longitude) {
		log.debug("Attempting to fetch forecast report from DB for lat={}, lon={}", latitude, longitude);
		return fetchAndSaveForecast(latitude, longitude);
	}

	/**
	 * Forces a refresh of the forecast report for a given latitude and longitude.
	 * This method bypasses any existing data and fetches a new forecast report from the provider.
	 *
	 * @param latitude  the latitude
	 * @param longitude the longitude
	 * @return an {@link Optional} containing the new {@link ForecastReport}, or empty if an error occurred.
	 */
	@Transactional
	public Optional<ForecastReport> forceRefreshForecastReport(double latitude, double longitude) {
		log.info("FORCE REFRESH: Evicting cache and fetching fresh data for lat={}, lon={}.", latitude, longitude);
		return fetchAndSaveForecast(latitude, longitude);
	}

	/**
	 * Fetches a new forecast from the external provider, creates or updates the location,
	 * and saves the new {@link ForecastReport} to the database.
	 *
	 * @param latitude The latitude for the forecast.
	 * @param longitude The longitude for the forecast.
	 * @return An {@link Optional} containing the newly saved {@link ForecastReport}.
	 */
	private Optional<ForecastReport> fetchAndSaveForecast(double latitude, double longitude) {
		try {
			// Find existing location or create a new one
			Location location = locationRepository.findByLatitudeAndLongitude(latitude, longitude)
					.orElseGet(() -> {
						Location newLocation = new Location();
						newLocation.setLatitude(latitude);
						newLocation.setLongitude(longitude);
						return locationRepository.save(newLocation);
					});

			RawForecastPayload payload = weatherProvider.fetchRawForecastPayload(location);

			ForecastReport report = new ForecastReport();
			report.setLocation(location);
			report.setFetchedAt(Instant.now());
			report.setRawPointsResponse(payload.rawPointsResponse());
			report.setRawHourlyResponse(payload.rawHourlyResponse());
			report.setRawDailyResponse(payload.rawDailyResponse());
			report.setRawGridDataResponse(payload.rawGridDataResponse());
			report.setRawObservationStationsResponse(payload.rawObservationStationsResponse());
			report.setRawObservationJson(payload.rawObservationJson());

			// The hourly predictions are derived from the raw hourly response for persistence.
			GridpointHourlyForecastJsonLd hourlyForecast = objectMapper.readValue(payload.rawHourlyResponse(), GridpointHourlyForecastJsonLd.class);
			List<HourlyPrediction> hourlyPredictions = toHourlyPredictions(hourlyForecast, report);
			report.setHourlyPredictions(hourlyPredictions);

			return Optional.of(forecastReportRepository.save(report));
		}
		catch (Exception e) {
			log.error("Failed to fetch and save forecast from provider {}", weatherProvider.getType(), e);
		}
		return Optional.empty();
	}

	/**
	 * Gets simplified weather data for the global weather widget based on a zip code.
	 * This method is designed to be lightweight for UI components.
	 *
	 * @param zipCode the zip code to get the forecast for
	 * @return an {@link Optional} containing the {@link WeatherWidgetPayload}, or empty if not found.
	 */
	public Optional<WeatherWidgetPayload> getForecastByZipCode(String zipCode) {
		// Use the ZipCodeService to get coordinates, which is also cached.
		return zipCodeService.getCoordinates(zipCode)
				.flatMap(places -> {
					if (places.isEmpty()) {
						return Optional.empty();
					}
					ZipCodeCache place = places.getFirst();
					// Get the raw hourly forecast string and package it for the client.
					return getRawForecasts(place.getLatitude(), place.getLongitude())
							.map(rawPayload -> new WeatherWidgetPayload(rawPayload.rawHourlyResponse(), place.getPlaceName()));
				});
	}

	public Optional<WeatherWidgetDTO> getWeatherWidgetData(String zipCode) {
		log.debug("Getting weather widget data for zip code: {}", zipCode);
		return getForecastByZipCode(zipCode)
				.flatMap(payload -> {
					log.debug("Found forecast payload for zip code: {}", zipCode);
					try {
						JsonNode root = objectMapper.readTree(payload.forecast());
						JsonNode periods = root.path("periods");
						if (periods.isArray() && !periods.isEmpty()) {
							for (JsonNode period : periods) {
								Instant startTime = Instant.parse(period.path("startTime").asText());
								Instant endTime = Instant.parse(period.path("endTime").asText());
								Instant now = Instant.now();
								if (!now.isBefore(startTime) && now.isBefore(endTime)) {
									log.debug("Found current weather period for zip code: {}", zipCode);
									String temperature = period.path("temperature").asText();
									String description = period.path("shortForecast").asText();
									return Optional.of(new WeatherWidgetDTO(temperature, description));
								}
							}
							log.warn("Could not find current weather period for zip code: {}", zipCode);
						} else {
							log.warn("No weather periods found in forecast for zip code: {}. Raw forecast: {}", zipCode, payload.forecast());
						}
					} catch (Exception e) {
						log.error("Could not parse weather forecast JSON for zip code: {}", zipCode, e);
					}
					return Optional.empty();
				});
	}

	/**
	 * Gathers all necessary data for rendering the main weather page for a given user.
	 * This encapsulates the logic of getting user preferences, coordinates, and forecasts.
	 *
	 * @param user The authenticated user.
	 * @return An {@link Optional} containing the {@link WeatherPageData} DTO, or empty if data cannot be retrieved.
	 */
	public Optional<WeatherPageData> getWeatherPageDataForUser(User user) {
		if (user.getPreference() == null || user.getPreference().getZipCode() == null) {
			return Optional.empty();
		}

		return getWeatherPageDataForZipCode(user.getPreference().getZipCode());
	}

	/**
	 * Gathers all necessary data for rendering the main weather page for a given zip code.
	 *
	 * @param zipCode The zip code for which to retrieve weather page data.
	 * @return An {@link Optional} containing the {@link WeatherPageData} DTO.
	 */
	public Optional<WeatherPageData> getWeatherPageDataForZipCode(String zipCode) {
		return zipCodeService.getCoordinates(zipCode)
				.flatMap(places -> {
					if (places.isEmpty()) {
						return Optional.empty();
					}
					ZipCodeCache place = places.getFirst();
					return getRawForecasts(place.getLatitude(), place.getLongitude())
							.map(payload -> buildWeatherPageData(payload, place.getPlaceName()));
				});
	}

	/**
	 * Helper method to build the WeatherPageData DTO from a raw payload and location name.
	 * This encapsulates the logic for parsing the timezone and constructing the DTO.
	 *
	 * @param payload The raw forecast payload from the API.
	 * @param locationName The name of the location.
	 * @return A fully constructed {@link WeatherPageData} object.
	 */
	private WeatherPageData buildWeatherPageData(RawForecastPayload payload, String locationName) {
		String timeZone = "UTC"; // Fallback
		try {
			if (payload.rawPointsResponse() != null) {
				PointJsonLd pointsData = objectMapper.readValue(payload.rawPointsResponse(), PointJsonLd.class);
				timeZone = pointsData.getTimeZone();
			}
		} catch (Exception e) {
			log.error("Could not parse timezone from points response, falling back to UTC.", e);
		}

		return WeatherPageData.builder()
				.rawHourlyForecastJson(payload.rawHourlyResponse())
				.rawDailyForecastJson(payload.rawDailyResponse())
				.rawPointsJson(payload.rawPointsResponse())
				.rawObservationStationsJson(payload.rawObservationStationsResponse())
				.rawObservationJson(payload.rawObservationJson())
				.locationName(locationName)
				.locationTimeZone(timeZone)
				.build();
	}

	/**
	 * Forces a refresh of the forecast report for a given user based on their saved preferences.
	 *
	 * @param user The authenticated user.
	 */
	public void forceRefreshForUser(User user) {
		zipCodeService.getCoordinates(user.getPreference().getZipCode())
				.ifPresent(places -> {
					if (!places.isEmpty()) {
						ZipCodeCache place = places.getFirst();
						forceRefreshForecastReport(place.getLatitude(), place.getLongitude());
					}
				});
	}

	/**
	 * Retrieves the raw forecast payloads from the database for a given location.
	 *
	 * @param latitude The latitude of the location.
	 * @param longitude The longitude of the location.
	 * @return An {@link Optional} containing the {@link RawForecastPayload}.
	 */
	public Optional<RawForecastPayload> getRawForecasts(double latitude, double longitude) {
		log.info("Fetching raw forecasts from DB for lat={}, lon={}", latitude, longitude);
		return getForecastReportFromDb(latitude, longitude)
				.map(report -> new RawForecastPayload(
						report.getRawPointsResponse(),
						report.getRawHourlyResponse(),
						report.getRawDailyResponse(),
						report.getRawGridDataResponse(),
						report.getRawObservationStationsResponse(),
						report.getRawObservationJson()
				));
	}

	/**
	 * Retrieves and deserializes the raw forecast data into a structured {@link ProcessedForecast} object.
	 *
	 * @param latitude The latitude of the location.
	 * @param longitude The longitude of the location.
	 * @return An {@link Optional} containing the {@link ProcessedForecast}.
	 */
	public Optional<ProcessedForecast> getProcessedForecast(double latitude, double longitude) {
		log.info("Fetching and parsing processed forecast for lat={}, lon={}", latitude, longitude);
		return getRawForecasts(latitude, longitude)
				.flatMap(this::parseRawPayloadToProcessedForecast);
	}

	/**
	 * Parses the raw JSON strings from a {@link RawForecastPayload} into their corresponding structured DTOs.
	 *
	 * @param payload The raw forecast payload.
	 * @return An {@link Optional} containing the fully parsed {@link ProcessedForecast}.
	 */
	private Optional<ProcessedForecast> parseRawPayloadToProcessedForecast(RawForecastPayload payload) {
		try {
			PointJsonLd pointsData = objectMapper.readValue(payload.rawPointsResponse(), PointJsonLd.class);
			GridpointHourlyForecastJsonLd hourlyData = objectMapper.readValue(payload.rawHourlyResponse(), GridpointHourlyForecastJsonLd.class);
			Gridpoint12hForecastJsonLd dailyData = objectMapper.readValue(payload.rawDailyResponse(), Gridpoint12hForecastJsonLd.class);
			Gridpoint gridData = objectMapper.readValue(payload.rawGridDataResponse(), Gridpoint.class);

			return Optional.of(ProcessedForecast.builder()
					.pointsData(pointsData)
					.hourlyForecast(hourlyData)
					.dailyForecast(dailyData)
					.gridData(gridData)
					.build());
		}
		catch (Exception e) {
			log.error("Failed to parse raw payload into ProcessedForecast", e);
			return Optional.empty();
		}
	}

	/**
	 * Converts the NWS API's hourly forecast periods into a list of {@link HourlyPrediction}
	 * entities suitable for persistence.
	 *
	 * @param hourlyForecast The deserialized hourly forecast data from the NWS API.
	 * @param report The parent {@link ForecastReport} to associate with the predictions.
	 * @return A list of {@link HourlyPrediction} entities.
	 */
	private List<HourlyPrediction> toHourlyPredictions(GridpointHourlyForecastJsonLd hourlyForecast, ForecastReport report) {
		if (hourlyForecast == null || hourlyForecast.getPeriods() == null) {
			return List.of();
		}
		return hourlyForecast.getPeriods().stream()
				.map(period -> {
					var prediction = new HourlyPrediction();
					prediction.setReport(report); // Set back-reference
					prediction.setStartTime(period.getStartTime().toInstant());
					prediction.setEndTime(period.getEndTime().toInstant());

					if (period.getTemperature() instanceof QuantitativeValue qv && qv.getValue() != null) {
						prediction.setTemperature(qv.getValue().intValue());
					}

					if (period.getTemperatureUnit() != null) {
						prediction.setTemperatureUnit(period.getTemperatureUnit().getValue());
					}

					prediction.setShortForecast(period.getShortForecast());
					return prediction;
				})
				.toList();
	}

	/**
	 * Checks if a weather provider is available for the user's location.
	 *
	 * @param user The user to check.
	 * @return true if a weather provider is available, false otherwise.
	 */
	public boolean isWeatherProviderAvailable(User user) {
		if (user == null || user.getPreference() == null || user.getPreference().getZipCode() == null) {
			return false;
		}
		return zipCodeService.getCoordinates(user.getPreference().getZipCode(), user)
				.map(places -> {
					if (places.isEmpty()) {
						return false;
					}
					ZipCodeCache place = places.getFirst();
					// NWS provider is only available for US
					return "US".equals(place.getCountryAbbreviation());
				}).orElse(false);
	}

	/**
	 * Retrieves a fully processed forecast for a given user, based on their saved zip code.
	 *
	 * @param user The user for whom to get the forecast.
	 * @return An {@link Optional} containing the {@link ProcessedForecast}.
	 */
	public Optional<ProcessedForecast> getProcessedForecastForUser(User user) {
		if (user.getPreference() == null || user.getPreference().getZipCode() == null) {
			return Optional.empty();
		}
		return zipCodeService.getCoordinates(user.getPreference().getZipCode(), user)
				.flatMap(places -> {
					if (places.isEmpty()) {
						return Optional.empty();
					}
					ZipCodeCache place = places.getFirst();
					return getProcessedForecast(place.getLatitude(), place.getLongitude());
				});
	}
}