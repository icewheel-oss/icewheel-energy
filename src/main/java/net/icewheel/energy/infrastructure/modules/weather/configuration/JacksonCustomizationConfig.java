package net.icewheel.energy.infrastructure.modules.weather.configuration;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.GridpointHourlyForecastPeriodTemperatureDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.GridpointHourlyForecastPeriodWindGustDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.GridpointHourlyForecastPeriodWindSpeedDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.ISO8601IntervalDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.JsonLdContextDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.PointRelativeLocationDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.WindDirectionEnumDeserializer;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GeoJSONLineString;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GeoJSONMultiLineString;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GeoJSONMultiPoint;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GeoJSONMultiPolygon;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GeoJSONPoint;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GeoJSONPolygon;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GeoJsonGeometry;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriod;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriodTemperature;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriodWindGust;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriodWindSpeed;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.ISO8601Interval;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.JsonLdContext;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.PointRelativeLocation;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.QuantitativeValue;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonCustomizationConfig {

    /**
     * Customizes the default Jackson ObjectMapper used by Spring Boot.
     * This is the standard way to register mix-ins for polymorphic deserialization
     * and custom deserializers for complex types.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                // Mix-in for handling polymorphic GeoJSON geometry types
                .mixIn(GeoJsonGeometry.class, GeoJsonGeometryMixin.class)

                // Mix-in to apply a custom serializer specifically for the 'temperature' field
                .mixIn(GridpointHourlyForecastPeriod.class, GridpointHourlyForecastPeriodMixin.class)

                // Custom deserializers for complex or polymorphic NWS API types
                .deserializerByType(PointRelativeLocation.class, new PointRelativeLocationDeserializer())
                .deserializerByType(JsonLdContext.class, new JsonLdContextDeserializer())
                .deserializerByType(ISO8601Interval.class, new ISO8601IntervalDeserializer())
                .deserializerByType(GridpointHourlyForecastPeriodTemperature.class, new GridpointHourlyForecastPeriodTemperatureDeserializer())
                .deserializerByType(GridpointHourlyForecastPeriodWindSpeed.class, new GridpointHourlyForecastPeriodWindSpeedDeserializer())
                .deserializerByType(GridpointHourlyForecastPeriodWindGust.class, new GridpointHourlyForecastPeriodWindGustDeserializer())
                .deserializerByType(GridpointHourlyForecastPeriod.WindDirectionEnum.class, new WindDirectionEnumDeserializer());
    }

    /**
     * A mix-in for the {@link GeoJsonGeometry} interface. This tells Jackson how to
     * deserialize the object based on the "type" property in the JSON, mapping it
     * to the correct concrete GeoJSON class (e.g., Point, Polygon).
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = GeoJSONPoint.class, name = "Point"),
            @JsonSubTypes.Type(value = GeoJSONPolygon.class, name = "Polygon"),
            @JsonSubTypes.Type(value = GeoJSONLineString.class, name = "LineString"),
            @JsonSubTypes.Type(value = GeoJSONMultiPoint.class, name = "MultiPoint"),
            @JsonSubTypes.Type(value = GeoJSONMultiPolygon.class, name = "MultiPolygon"),
            @JsonSubTypes.Type(value = GeoJSONMultiLineString.class, name = "MultiLineString")
    })
    abstract static class GeoJsonGeometryMixin {
    }

    /**
     * A custom serializer for the temperature field, which can be a complex object.
     * This serializer ensures that only the numeric value is written to the JSON,
     * which is what the frontend UI expects.
     */
    public static class TemperatureSerializer extends JsonSerializer<GridpointHourlyForecastPeriodTemperature> {
        @Override
        public void serialize(GridpointHourlyForecastPeriodTemperature temp, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (temp instanceof QuantitativeValue qv && qv.getValue() != null) {
                gen.writeNumber(qv.getValue());
            } else {
                gen.writeNull();
            }
        }
    }

    abstract static class GridpointHourlyForecastPeriodMixin {
        @JsonSerialize(using = TemperatureSerializer.class)
        public abstract GridpointHourlyForecastPeriodTemperature getTemperature();
    }
}
