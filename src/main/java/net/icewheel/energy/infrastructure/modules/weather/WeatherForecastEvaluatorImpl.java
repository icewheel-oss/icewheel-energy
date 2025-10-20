package net.icewheel.energy.infrastructure.modules.weather;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.modules.weather.dto.ProcessedForecast;
import net.icewheel.energy.infrastructure.modules.weather.exception.ForecastEvaluationException;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriod;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointQuantitativeValueLayer;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointQuantitativeValueLayerValuesInner;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.QuantitativeValue;

import org.springframework.stereotype.Component;

/**
 * An implementation of {@link WeatherForecastEvaluator} that uses data from the
 * National Weather Service (NWS) to produce a quantitative solar forecast.
 * <p>
 * This implementation is responsible for:
 * <ul>
 *     <li>Determining the correct evaluation date (today or tomorrow).</li>
 *     <li>Defining a "solar window" based on dynamic sunrise/sunset times or seasonal fallbacks.</li>
 *     <li>Analyzing NWS grid data for average cloud cover within the solar window.</li>
 *     <li>Analyzing hourly forecast data for maximum precipitation probability.</li>
 *     <li>Calculating a final "sunshine percentage" based on these weather factors.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherForecastEvaluatorImpl implements WeatherForecastEvaluator {

	// Default fallback solar window start and end times.
	private static final LocalTime FALLBACK_SOLAR_START = LocalTime.of(8, 0); // 8 AM
	private static final LocalTime FALLBACK_SOLAR_END = LocalTime.of(17, 0);   // 5 PM

	// Offsets to account for negligible solar generation right at sunrise and sunset.
	private static final int SUNRISE_OFFSET_HOURS = 1;
	private static final int SUNSET_OFFSET_HOURS = 1;

	private final Clock clock;
	private final WeatherService weatherService;

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation performs a multi-step analysis to generate a solar forecast:
	 * <ol>
	 *     <li>It fetches the latest processed forecast data for the user's location.</li>
	 *     <li>It determines the correct day to evaluate (today or tomorrow) by checking if the
	 *     current time is already past today's sunset.</li>
	 *     <li>It calculates the effective "solar window" for that day, preferring dynamic
	 *     sunrise/sunset times and using seasonal fallbacks if necessary.</li>
	 *     <li>Within this window, it calculates the average cloud cover and the maximum
	 *     probability of precipitation.</li>
	 *     <li>Finally, it synthesizes these factors into a single "sunshine percentage" and
	 *     returns it along with a descriptive reason.</li>
	 * </ol>
	 */
	@Override
	public SolarForecast evaluate(User user) {
		// 1. Fetch the raw forecast data for the user's configured location.
		Optional<ProcessedForecast> forecastOpt = weatherService.getProcessedForecastForUser(user);

		if (forecastOpt.isEmpty()) {
			// If no forecast data is available (e.g., for a non-US location or due to an API error),
			// we gracefully handle it by assuming good weather. This prevents forced charging
			// and disables the weather-aware feature for users without a valid forecast.
			log.warn("Could not retrieve processed forecast for user. Assuming good weather and skipping evaluation.");
			return new SolarForecast(100, "Could not retrieve weather forecast for the user's location. Assuming good weather.", Map.of());
		}

		ProcessedForecast forecast = forecastOpt.get();
		if (forecast == null || forecast.getPointsData() == null || forecast.getHourlyForecast() == null || forecast.getGridData() == null) {
			throw new ForecastEvaluationException("Incomplete forecast data provided for evaluation.");
		}

		if (forecast.getPointsData().getTimeZone() == null) {
			throw new ForecastEvaluationException("Forecast data is missing timezone information.");
		}

		ZoneId forecastTimeZone = ZoneId.of(forecast.getPointsData().getTimeZone());
		ZonedDateTime nowInForecastZone = ZonedDateTime.now(clock.withZone(forecastTimeZone));

		// 2. Determine the correct date to evaluate.
		// If it's already past sunset today, evaluate the forecast for tomorrow.
		LocalDate evaluationDate = nowInForecastZone.toLocalDate();
		Optional<ZonedDateTime> todaySunsetOpt = findBoundary(forecast, evaluationDate, false, forecastTimeZone);
		if (todaySunsetOpt.isPresent() && nowInForecastZone.isAfter(todaySunsetOpt.get())) {
			log.debug("Current time is past today's sunset. Evaluating forecast for tomorrow.");
			evaluationDate = evaluationDate.plusDays(1);
		}

		// 3. Define the effective solar window for the evaluation date.
		final ZonedDateTime startOfSolarWindow;
		final ZonedDateTime endOfSolarWindow;

		Optional<ZonedDateTime> sunriseOpt = findBoundary(forecast, evaluationDate, true, forecastTimeZone);
		Optional<ZonedDateTime> sunsetOpt = findBoundary(forecast, evaluationDate, false, forecastTimeZone);

		if (sunriseOpt.isPresent() && sunsetOpt.isPresent()) {
			// Use dynamic sunrise/sunset times, but offset by an hour on each side, as generation
			// is negligible at the very beginning and end of the day.
			ZonedDateTime sunrise = sunriseOpt.get();
			ZonedDateTime sunset = sunsetOpt.get();

			ZonedDateTime effectiveStart = sunrise.plusHours(SUNRISE_OFFSET_HOURS);
			ZonedDateTime effectiveEnd = sunset.minusHours(SUNSET_OFFSET_HOURS);

			// On very short days (e.g., in winter), the offsets might make the window invalid.
			// In that case, we revert to using the original, un-offset sunrise/sunset times.
			if (effectiveStart.isAfter(effectiveEnd)) {
				log.warn("Solar window for {} is too short for offsets ({} to {}). Using original sunrise/sunset.", evaluationDate, sunrise, sunset);
				startOfSolarWindow = sunrise;
				endOfSolarWindow = sunset;
			} else {
				log.debug("Using dynamic solar window for {} (with offsets): {} to {}", evaluationDate, effectiveStart, effectiveEnd);
				startOfSolarWindow = effectiveStart;
				endOfSolarWindow = effectiveEnd;
			}
		} else {
			// If sunrise/sunset data is unavailable, fall back to a reasonable fixed window based on the season.
			log.warn("Could not determine sunrise/sunset for {}. Falling back to seasonal fixed window.", evaluationDate);
			LocalTime[] seasonalWindow = getSeasonalFixedWindow(evaluationDate.getMonth());
			startOfSolarWindow = evaluationDate.atTime(seasonalWindow[0]).atZone(forecastTimeZone);
			endOfSolarWindow = evaluationDate.atTime(seasonalWindow[1]).atZone(forecastTimeZone);
		}

		// 4. Filter the hourly forecast data to only include periods within our defined solar window.
		List<GridpointHourlyForecastPeriod> relevantPeriods = forecast.getHourlyForecast().getPeriods().stream()
				.filter(period -> {
					assert period.getStartTime() != null;
					ZonedDateTime periodStartTime = period.getStartTime().atZoneSameInstant(forecastTimeZone);
					return !periodStartTime.isBefore(startOfSolarWindow) && periodStartTime.isBefore(endOfSolarWindow);
				})
				.toList();

		// Access the skyCover data layer using the correct method for dynamic properties.
		GridpointQuantitativeValueLayer skyCoverLayer = forecast.getGridData().getAdditionalProperty("skyCover");

		if (relevantPeriods.isEmpty() || skyCoverLayer == null || skyCoverLayer.getValues() == null) {
			throw new ForecastEvaluationException("No forecast data found within the effective solar window.");
		}

		// 5. Calculate the average cloud cover during the solar window.
		double avgCloudCover = skyCoverLayer.getValues().stream()
				.filter(Objects::nonNull) // Add null-check for robustness
				.filter(v -> {
					if (v.getValidTime() == null) {
						return false;
					}
					// The validTime is an ISO8601 interval string like "2025-09-01T12:00:00+00:00/PT1H".
					// We must call toString() to get the string representation of the interval before splitting.
					ZonedDateTime valueTime = ZonedDateTime.parse(v.getValidTime().toString().split("/")[0]).withZoneSameInstant(forecastTimeZone);
					return !valueTime.isBefore(startOfSolarWindow) && valueTime.isBefore(endOfSolarWindow);
				})
				// Use the correct class for the method reference.
				.map(GridpointQuantitativeValueLayerValuesInner::getValue)
				.filter(Objects::nonNull)
				.mapToDouble(java.math.BigDecimal::doubleValue)
				.average()
				.orElseThrow(() -> new ForecastEvaluationException("Could not calculate average cloud cover."));

		// 6. Calculate the maximum chance of precipitation during the solar window.
		double maxPrecipProbability = relevantPeriods.stream()
				.map(GridpointHourlyForecastPeriod::getProbabilityOfPrecipitation)
				.filter(Objects::nonNull)
				.map(QuantitativeValue::getValue)
				.filter(Objects::nonNull)
				.mapToDouble(java.math.BigDecimal::doubleValue)
				.max()
				.orElseThrow(() -> new ForecastEvaluationException("Could not calculate max precipitation probability."));

		// 7. Calculate the final "sunshine percentage".
		// This formula starts with 100%, subtracts the cloud cover, and then further reduces
		// the remaining potential by the chance of rain.
		double sunshinePotential = 100.0 - avgCloudCover;
		double finalSunshine = sunshinePotential * (1 - (maxPrecipProbability / 100.0));
		int sunshinePercentage = (int) Math.round(finalSunshine);

		// 8. Build the final result with a human-readable reason and metadata for logging.
		Map<String, String> metadata = new HashMap<>();
		metadata.put("Solar Window Start", startOfSolarWindow.toString());
		metadata.put("Solar Window End", endOfSolarWindow.toString());
		metadata.put("Avg. Cloud Cover", String.format("%.0f%%", avgCloudCover));
		metadata.put("Max Precip. Chance", String.format("%.0f%%", maxPrecipProbability));
		metadata.put("Calculated Sunshine", String.format("%d%%", sunshinePercentage));

		String reason = String.format(
				"Solar potential is %d%% based on avg. cloud cover of %.0f%% and max precip. chance of %.0f%%.",
				sunshinePercentage, avgCloudCover, maxPrecipProbability
		);

		return new SolarForecast(sunshinePercentage, reason, metadata);
	}

	/**
	 * Provides a reasonable fixed solar window based on the season, used as a fallback
	 * when dynamic sunrise/sunset times cannot be determined.
	 * @param month The month of the evaluation date.
	 * @return An array containing the start and end LocalTime for the solar window.
	 */
	private LocalTime[] getSeasonalFixedWindow(Month month) {
		return switch (month) {
			// Winter: Shorter solar window
			case DECEMBER, JANUARY, FEBRUARY -> new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(16, 0)};
			// Summer: Longer solar window
			case JUNE, JULY, AUGUST -> new LocalTime[]{LocalTime.of(7, 0), LocalTime.of(18, 0)};
			// Spring/Fall: Default window
			default -> new LocalTime[]{FALLBACK_SOLAR_START, FALLBACK_SOLAR_END};
		};
	}

	/**
	 * Finds the start time of the first daily forecast period that matches the desired daytime status.
	 * This is used to infer sunrise (isDaytime=true) and sunset (isDaytime=false).
	 *
	 * @param forecast The processed forecast data.
	 * @param date The specific date to search within.
	 * @param findDaytime True to find the first daytime period (sunrise), false for the first nighttime period (sunset).
	 * @param forecastTimeZone The timezone of the forecast location.
	 * @return An optional containing the start time of the relevant period.
	 */
	private Optional<ZonedDateTime> findBoundary(ProcessedForecast forecast, LocalDate date, boolean findDaytime, ZoneId forecastTimeZone) {
		if (forecast.getDailyForecast() == null || forecast.getDailyForecast().getPeriods() == null) {
			return Optional.empty();
		}
		return forecast.getDailyForecast().getPeriods().stream()
				.filter(period -> period.getStartTime().atZoneSameInstant(forecastTimeZone).toLocalDate().equals(date))
				.filter(period -> Boolean.valueOf(findDaytime).equals(period.getIsDaytime()))
				.map(period -> period.getStartTime().atZoneSameInstant(forecastTimeZone))
				.findFirst();
	}
}