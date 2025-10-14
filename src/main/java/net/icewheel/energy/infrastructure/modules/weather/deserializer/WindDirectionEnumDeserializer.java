
package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.GridpointHourlyForecastPeriod.WindDirectionEnum;

public class WindDirectionEnumDeserializer extends StdDeserializer<WindDirectionEnum> {

    public WindDirectionEnumDeserializer() {
        this(null);
    }

    public WindDirectionEnumDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public WindDirectionEnum deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        String value = jp.getText();
        if (value == null || value.isEmpty()) {
            return null;
        }
        return WindDirectionEnum.fromValue(value);
    }
}
