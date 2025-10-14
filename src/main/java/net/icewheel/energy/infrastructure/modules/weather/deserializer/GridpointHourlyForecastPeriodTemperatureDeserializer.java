
package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriodTemperature;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.QuantitativeValue;

public class GridpointHourlyForecastPeriodTemperatureDeserializer extends StdDeserializer<GridpointHourlyForecastPeriodTemperature> {

    public GridpointHourlyForecastPeriodTemperatureDeserializer() {
        this(null);
    }

    public GridpointHourlyForecastPeriodTemperatureDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public GridpointHourlyForecastPeriodTemperature deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (node.isObject()) {
            return jp.getCodec().treeToValue(node, QuantitativeValue.class);
        }
        return null;
    }
}
