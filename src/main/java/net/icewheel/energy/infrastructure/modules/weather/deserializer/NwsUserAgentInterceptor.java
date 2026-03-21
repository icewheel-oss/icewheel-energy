package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;

import net.icewheel.energy.infrastructure.modules.weather.configuration.NwsProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
public class NwsUserAgentInterceptor implements ClientHttpRequestInterceptor {

    private final NwsProperties nwsProperties;

    public NwsUserAgentInterceptor(NwsProperties nwsProperties) {
        this.nwsProperties = nwsProperties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add(HttpHeaders.USER_AGENT, nwsProperties.getUserAgent());
        return execution.execute(request, body);
    }
}
