package net.icewheel.energy.infrastructure.modules.weather.dto;

import lombok.Value;

@Value
public class WeatherWidgetData {
    String temperature;
    String shortForecast;
    String icon;
    String locationName;
}
