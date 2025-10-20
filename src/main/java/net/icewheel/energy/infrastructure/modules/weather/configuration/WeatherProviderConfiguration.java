package net.icewheel.energy.infrastructure.modules.weather.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.icewheel.energy.infrastructure.modules.weather.NWSProvider;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WeatherProviderConfiguration {

    @Bean
    public NWSProvider nwsProvider(@Qualifier("nwsRestClient") RestClient restClient, ObjectMapper objectMapper) {
        return new NWSProvider(restClient, objectMapper);
    }
}