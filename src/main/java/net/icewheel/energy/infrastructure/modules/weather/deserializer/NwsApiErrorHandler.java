package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;

import net.icewheel.energy.api.rest.exception.NwsApiException;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

public class NwsApiErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        throw new NwsApiException("NWS API error: " + response.getStatusText());
    }
}
