package net.icewheel.energy.infrastructure.modules.weather;

import net.icewheel.energy.infrastructure.modules.weather.dto.RawForecastPayload;
import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderType;

public interface WeatherProvider {

	WeatherProviderType getType();

	/**
	 * Fetches all raw forecast payloads from the weather provider for a given location.
	 * This method is responsible for making all necessary external API calls.
	 * @param location The location for which to fetch the forecast.
	 * @return A {@link RawForecastPayload} containing the raw JSON strings from the API.
	 */
	RawForecastPayload fetchRawForecastPayload(Location location);

}
