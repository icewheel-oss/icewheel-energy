package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.Alert;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.AlertCollectionJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.AlertsActiveCount200Response;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.AlertsTypes200Response;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.Gridpoint12hForecastJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointGeoJson;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.ObservationCollectionJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.ObservationGeoJson;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.ObservationStationGeoJson;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.PointJsonLd;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.TextProduct;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.TextProductCollection;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.TextProductLocationCollection;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.TextProductTypeCollection;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NwsWebClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NwsWebClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, NwsUserAgentInterceptor nwsUserAgentInterceptor) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.weather.gov")
                .requestInterceptor(nwsUserAgentInterceptor)
                .defaultStatusHandler(new NwsApiErrorHandler())
                .build();
        this.objectMapper = objectMapper;
    }

    public AlertCollectionJsonLd getAlerts(Boolean active, String start, String end, String status, String messageType, String event, String code, String area, String point, String region, String regionType, String zone, String urgency, String severity, String certainty, Integer limit, String cursor) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/alerts");
        if (active != null) {
            builder.queryParam("active", active);
        }
        if (start != null) {
            builder.queryParam("start", start);
        }
        if (end != null) {
            builder.queryParam("end", end);
        }
        if (status != null) {
            builder.queryParam("status", status);
        }
        if (messageType != null) {
            builder.queryParam("message_type", messageType);
        }
        if (event != null) {
            builder.queryParam("event", event);
        }
        if (code != null) {
            builder.queryParam("code", code);
        }
        if (area != null) {
            builder.queryParam("area", area);
        }
        if (point != null) {
            builder.queryParam("point", point);
        }
        if (region != null) {
            builder.queryParam("region", region);
        }
        if (regionType != null) {
            builder.queryParam("region_type", regionType);
        }
        if (zone != null) {
            builder.queryParam("zone", zone);
        }
        if (urgency != null) {
            builder.queryParam("urgency", urgency);
        }
        if (severity != null) {
            builder.queryParam("severity", severity);
        }
        if (certainty != null) {
            builder.queryParam("certainty", certainty);
        }
        if (limit != null) {
            builder.queryParam("limit", limit);
        }
        if (cursor != null) {
            builder.queryParam("cursor", cursor);
        }

        return this.restClient.get()
                .uri(builder.toUriString())
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body(AlertCollectionJsonLd.class);
    }

    public AlertsActiveCount200Response getActiveAlertsCount() {
        return this.restClient.get()
                .uri("/alerts/active/count")
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( AlertsActiveCount200Response.class);
    }

    public AlertsTypes200Response getAlertTypes() {
        return this.restClient.get()
                .uri("/alerts/types")
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( AlertsTypes200Response.class);
    }

    /**
     * Get all active alerts.
     *
     * @return A collection of active alerts.
     */
    public AlertCollectionJsonLd getActiveAlerts() {
        return this.restClient.get()
                .uri("/alerts/active")
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( AlertCollectionJsonLd.class);
    }

    

    /**
     * Get an alert by its ID.
     *
     * @param id The ID of the alert to retrieve.
     * @return The alert with the specified ID.
     */
    public Alert getAlertById(String id) {
        return this.restClient.get()
                .uri("/alerts/{id}", id)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( Alert.class);
    }

    /**
     * Get the NWS glossary.
     *
     * @return The NWS glossary.
     */
    public Object getGlossary() {
        return this.restClient.get()
                .uri("/glossary")
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( Object.class);
    }

    

    public GridpointGeoJson getGridpoint(String wfo, Integer x, Integer y) {
        return this.restClient.get()
                .uri("/gridpoints/{wfo}/{x},{y}", wfo, x, y)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( GridpointGeoJson.class);
    }

    public Gridpoint12hForecastJsonLd getGridpointForecast(String wfo, Integer x, Integer y) {
        return this.restClient.get()
                .uri("/gridpoints/{wfo}/{x},{y}/forecast", wfo, x, y)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body(Gridpoint12hForecastJsonLd.class);
    }

    public GridpointHourlyForecastJsonLd getGridpointForecastHourly(String wfo, Integer x, Integer y) {
        return this.restClient.get()
                .uri("/gridpoints/{wfo}/{x},{y}/forecast/hourly", wfo, x, y)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body(GridpointHourlyForecastJsonLd.class);
    }

    public String getGridpointStations(String wfo, Integer x, Integer y, Integer limit) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/gridpoints/{wfo}/{x},{y}/stations");
        if (limit != null) {
            builder.queryParam("limit", limit);
        }

        return this.restClient.get()
                .uri(builder.buildAndExpand(wfo, x, y).toUriString())
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( String.class);
    }

    public String getGridpointStations(double latitude, double longitude, Integer limit) {
        PointJsonLd point = getGridpoints(latitude, longitude);
        return getGridpointStations(Objects.requireNonNull(point.getGridId()).toString(), point.getGridX(), point.getGridY(), limit);
    }

    public ObservationCollectionJsonLd getStationObservations(String stationId, String start, String end, Integer limit) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/stations/{stationId}/observations");
        if (start != null) {
            builder.queryParam("start", start);
        }
        if (end != null) {
            builder.queryParam("end", end);
        }
        if (limit != null) {
            builder.queryParam("limit", limit);
        }

        return this.restClient.get()
                .uri(builder.buildAndExpand(stationId).toUriString())
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body(ObservationCollectionJsonLd.class);
    }

    public ObservationGeoJson getLatestStationObservation(String stationId, Boolean requireQc) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/stations/{stationId}/observations/latest");
        if (requireQc != null) {
            builder.queryParam("require_qc", requireQc);
        }

        return this.restClient.get()
                .uri(builder.buildAndExpand(stationId).toUriString())
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( ObservationGeoJson.class);
    }

    public ObservationGeoJson getStationObservationByTime(String stationId, String time) {
        return this.restClient.get()
                .uri("/stations/{stationId}/observations/{time}", stationId, time)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( ObservationGeoJson.class);
    }

    public TextProductLocationCollection getProductLocations() {
        return this.restClient.get()
                .uri("/products/locations")
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( TextProductLocationCollection.class);
    }

    public TextProductTypeCollection getProductTypes() {
        return this.restClient.get()
                .uri("/products/types")
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( TextProductTypeCollection.class);
    }

    public TextProduct getProductById(String productId) {
        return this.restClient.get()
                .uri("/products/{productId}", productId)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( TextProduct.class);
    }

    public TextProductCollection getProductsByType(String typeId) {
        return this.restClient.get()
                .uri("/products/types/{typeId}", typeId)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( TextProductCollection.class);
    }

    public TextProductLocationCollection getProductTypeLocations(String typeId) {
        return this.restClient.get()
                .uri("/products/types/{typeId}/locations", typeId)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( TextProductLocationCollection.class);
    }

    public TextProductTypeCollection getLocationProducts(String locationId) {
        return this.restClient.get()
                .uri("/products/locations/{locationId}/types", locationId)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( TextProductTypeCollection.class);
    }

    public TextProductCollection getProductsByTypeAndLocation(String typeId, String locationId) {
        return this.restClient.get()
                .uri("/products/types/{typeId}/locations/{locationId}", typeId, locationId)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( TextProductCollection.class);
    }

    /**
     * Get a list of weather stations.
     *
     * @param id     A list of station IDs to retrieve.
     * @param state  A list of states to retrieve stations for.
     * @param limit  The maximum number of stations to retrieve.
     * @param cursor A cursor for pagination.
     * @return A collection of weather stations.
     */
    public String getStations(List<String> id, List<String> state, Integer limit, String cursor) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/stations");
        if (id != null && !id.isEmpty()) {
            builder.queryParam("id", String.join(",", id));
        }
        if (state != null && !state.isEmpty()) {
            builder.queryParam("state", String.join(",", state));
        }
        if (limit != null) {
            builder.queryParam("limit", limit);
        }
        if (cursor != null) {
            builder.queryParam("cursor", cursor);
        }

        return this.restClient.get()
                .uri(builder.toUriString())
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( String.class);
    }

    

    public PointJsonLd getGridpoints(double latitude, double longitude) {
        return this.restClient.get()
                .uri("/points/{latitude},{longitude}", latitude, longitude)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                .body(PointJsonLd.class);
    }

    

    /**
     * Get a weather station by its ID.
     *
     * @param stationId The ID of the station to retrieve.
     * @return The weather station with the specified ID.
     */
    public ObservationStationGeoJson getStation(String stationId) {
        return this.restClient.get()
                .uri("/stations/{stationId}", stationId)
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( ObservationStationGeoJson.class);
    }

    /**
     * Get a list of weather products.
     *
     * @return A collection of weather products.
     */
    public Object getProducts() {
        return this.restClient.get()
                .uri("/products")
                .accept(new MediaType("application", "ld+json"))
                .retrieve()
                                .body( Object.class);
    }

    
}
