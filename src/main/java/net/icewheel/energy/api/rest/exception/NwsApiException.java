package net.icewheel.energy.api.rest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class NwsApiException extends RuntimeException {

    public NwsApiException(String message) {
        super(message);
    }

    public NwsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
