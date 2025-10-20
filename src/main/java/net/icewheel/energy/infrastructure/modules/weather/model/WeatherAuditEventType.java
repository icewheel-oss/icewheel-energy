package net.icewheel.energy.infrastructure.modules.weather.model;

/**
 * An enumeration of the types of weather-related audit events.
 */
public enum WeatherAuditEventType {
    /**
     * A weather forecast was successfully fetched from a provider.
     */
    FORECAST_FETCH_SUCCESS,
    /**
     * Failed to fetch a weather forecast from a provider.
     */
    FORECAST_FETCH_FAILURE,
    /**
     * The weather forecast was evaluated as "good", so no action was taken.
     */
    EVALUATION_GOOD_WEATHER,
    /**
     * The weather forecast was evaluated as "bad", which may trigger a forced charge.
     */
    EVALUATION_BAD_WEATHER,
    /**
     * A temporary "forced charge" schedule was created due to a bad weather forecast.
     */
    FORCED_CHARGE_INITIATED,
    /**
     * A forced charge was skipped for a reason (e.g., already charged, user setting).
     */
    FORCED_CHARGE_SKIPPED,

	/**
	 * 
	 */
	EVALUATION_FAILURE,
	/**
	 * The weather forecast was successfully evaluated.
	 */
	EVALUATION_SUCCESS
}