package net.icewheel.energy.infrastructure.modules.weather.dto;

import lombok.Builder;
import lombok.Value;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.Gridpoint;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.Gridpoint12hForecastJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.PointJsonLd;

@Value
@Builder
public class ProcessedForecast {
    PointJsonLd pointsData;
    GridpointHourlyForecastJsonLd hourlyForecast;
	Gridpoint12hForecastJsonLd dailyForecast;
	Gridpoint gridData;
    // You can add gridData and observationStations here in the future if needed.
}