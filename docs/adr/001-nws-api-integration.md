# ADR-001: NWS API Integration and Forecast Versioning

## Status

Accepted

## Context

The application required integration with the National Weather Service (NWS) API to provide weather-aware scheduling features. The existing weather service implementation was a placeholder and lacked the ability to fetch, store, and manage real forecast data. The NWS API presents a unique challenge as it is HATEOAS-based, requiring a multi-step process to retrieve a forecast. Additionally, to ensure data integrity and enable historical analysis, a versioning system for forecasts was necessary.

## Decision

We decided to implement a robust solution that addresses the complexities of the NWS API and the need for versioned forecast data. This involved a significant refactoring of the weather module, including changes to the data model, service layer, and infrastructure components.

### Key Changes and Rationale

1.  **HATEOAS-based API Client:**
    *   **What:** A new `NWSProvider` was implemented using Spring's `RestClient` and the `spring-boot-starter-hateoas` dependency.
    *   **Why:** The NWS API requires a two-step fetch process: first, a request to `/points/{lat},{lon}` to get a URL for the forecast, and then a second request to that URL. Spring HATEOAS with `EntityModel` provides a reliable way to parse the hypermedia links in the API response, making the client more resilient to URL changes.

2.  **Forecast Versioning Data Model:**
    *   **What:** A new data model was introduced with three JPA entities: `Location`, `ForecastReport`, and `HourlyPrediction`.
        *   `Location`: Stores geographic coordinates.
        *   `ForecastReport`: Stores a snapshot of each API fetch, including the raw JSON response.
        *   `HourlyPrediction`: Stores the time-specific forecast data for each report.
    *   **Why:** This model allows us to store multiple versions of a forecast for the same location. By preserving the raw data from each API call (`ForecastReport`), we can re-process historical data and debug issues without making repeated external API calls.

3.  **Refactored Service Layer:**
    *   **What:** The `WeatherProvider` interface was refactored to a single `fetchAndSaveForecast` method. The `WeatherService` was updated to use a "fetch-then-query" pattern.
    *   **Why:** This change simplifies the interaction with weather providers. The `WeatherService` now orchestrates the process of fetching and saving data first, and then querying the database for the latest forecast. This decouples the API interaction from the data retrieval logic.

4.  **Generated DTOs:**
    *   **What:** The `openapi-generator-maven-plugin` was configured to generate DTOs from the NWS OpenAPI specification into a specific package (`net.icewheel.energy.infrastructure.weather.nws.gen.model`).
    *   **Why:** Using generated DTOs ensures type safety and reduces the amount of boilerplate code needed to handle the API responses. Isolating them in a separate package prevents conflicts with manually created domain objects.

5.  **Integration Testing:**
    *   **What:** A new integration test, `NWSProviderIT`, was created using `MockRestServiceServer` to mock the NWS API responses.
    *   **Why:** This test validates the entire flow of the `NWSProvider`, from making the initial API call to parsing the response and persisting the data, without relying on the actual NWS API. This makes the tests faster, more reliable, and runnable in any environment.

6.  **Configuration:**
    *   **What:** The `RestClientConfig` was updated to provide a single, HATEOAS-enabled `RestClient` bean.
    *   **Why:** This centralizes the `RestClient` configuration and ensures that all components that use it (including the `NWSProvider`) have the necessary message converters to handle HATEOAS responses.

## Consequences

*   The application is now capable of fetching, storing, and retrieving versioned weather forecasts from the NWS.
*   The new data model provides a solid foundation for future features, such as historical weather analysis.
*   The integration tests ensure the reliability of the NWS client implementation.
*   The codebase is more modular and easier to maintain.
