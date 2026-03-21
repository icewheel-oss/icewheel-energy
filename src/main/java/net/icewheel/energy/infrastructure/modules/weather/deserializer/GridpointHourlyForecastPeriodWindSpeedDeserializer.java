
package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriodWindSpeed;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.QuantitativeValue;

public class GridpointHourlyForecastPeriodWindSpeedDeserializer extends StdDeserializer<GridpointHourlyForecastPeriodWindSpeed> {

    public GridpointHourlyForecastPeriodWindSpeedDeserializer() {
        this(null);
    }

    public GridpointHourlyForecastPeriodWindSpeedDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public GridpointHourlyForecastPeriodWindSpeed deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (node.isObject()) {
            return jp.getCodec().treeToValue(node, QuantitativeValue.class);
        }
        return null;
    }
}
