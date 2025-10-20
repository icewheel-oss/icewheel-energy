package net.icewheel.energy.infrastructure.modules.weather.model;

/**
 * An enumeration of the supported weather providers.
 *
 * <p>This enum is used to identify the different weather providers that are supported by
 * the application. It is used to look up the user's credentials for a provider, and
 * to select the correct provider implementation.
 */
public enum WeatherProviderType {

    /**
     * National Weather Service (NWS) provider.
     * @see <a href="https://www.weather.gov/documentation/services-web-api">https://www.weather.gov/documentation/services-web-api</a>
     */
    NWS("National Weather Service");

    /**
     * The display name of the weather provider.
     */
    private final String displayName;

    /**
     * Constructs a new WeatherProviderType.
     *
     * @param displayName The display name of the weather provider.
     */
    WeatherProviderType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name of the weather provider.
     *
     * @return The display name of the weather provider.
     */
    public String getDisplayName() {
        return displayName;
    }
}