package net.icewheel.energy.infrastructure.modules.weather.configuration;

import java.util.ArrayList;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Use the RestTemplateBuilder to get a pre-configured RestTemplate,
        // then customize it to add support for our custom media types.
        return builder.customizers(restTemplate -> {
            // Find the default Jackson message converter
            for (var converter : restTemplate.getMessageConverters()) {
                if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                    // Create a mutable copy of the supported media types
                    var supportedMediaTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                    // Add the custom media types required by the NWS API
                    supportedMediaTypes.add(new MediaType("application", "geo+json"));
                    supportedMediaTypes.add(new MediaType("application", "ld+json"));
                    jacksonConverter.setSupportedMediaTypes(supportedMediaTypes);
                    // We've found and modified the converter, so we can stop.
                    break;
                }
            }
        }).build();
    }

    @Bean
    public RestClient nwsRestClient(RestTemplate restTemplate, NwsProperties nwsProperties) {
        // Building the RestClient from the customized RestTemplate ensures it
        // inherits the message converters, including our custom media types.
		return RestClient.builder(restTemplate)
				.baseUrl(nwsProperties.getBaseUrl())
				.defaultHeader("User-Agent", nwsProperties.getUserAgent())
//				.accept(new MediaType("application", "ld+json"))
				.build();
    }
}