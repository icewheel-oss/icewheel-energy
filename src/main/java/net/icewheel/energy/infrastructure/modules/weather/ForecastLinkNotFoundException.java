package net.icewheel.energy.infrastructure.modules.weather;

public class ForecastLinkNotFoundException extends RuntimeException {

    public ForecastLinkNotFoundException(String message) {
        super(message);
    }

    public ForecastLinkNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
