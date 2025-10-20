
package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriodWindGust;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.QuantitativeValue;

public class GridpointHourlyForecastPeriodWindGustDeserializer extends StdDeserializer<GridpointHourlyForecastPeriodWindGust> {

    public GridpointHourlyForecastPeriodWindGustDeserializer() {
        this(null);
    }

    public GridpointHourlyForecastPeriodWindGustDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public GridpointHourlyForecastPeriodWindGust deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (node.isObject()) {
            return jp.getCodec().treeToValue(node, QuantitativeValue.class);
        }
        return null;
    }
}
