package net.icewheel.energy.infrastructure.modules.weather;

import net.icewheel.energy.application.user.model.User;

public interface WeatherForecastEvaluator {

    /**
     * Evaluates the weather forecast for a given user and produces a quantitative solar forecast.
     * <p>
     * This method encapsulates the core logic of the weather-aware feature. It determines
     * which day to analyze (today or tomorrow) based on the current time relative to sunset,
     * defines an effective "solar window" for that day, and then calculates the expected
     * solar generation potential based on meteorological data within that window.
     *
     * @param user The user for whom to evaluate the forecast. Their location is derived
     *             from their preferences.
     * @return A {@link SolarForecast} object containing the calculated "sunshine percentage"
     *         and a human-readable reason for the prediction.
     */
    SolarForecast evaluate(User user);
}
