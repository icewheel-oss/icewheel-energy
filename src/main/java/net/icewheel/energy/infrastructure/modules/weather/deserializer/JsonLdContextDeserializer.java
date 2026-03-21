package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.JsonLdContextArray;
import net.icewheel.energy.infrastructure.modules.weather.JsonLdContextObject;
import net.icewheel.energy.infrastructure.modules.weather.JsonLdContextString;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.JsonLdContext;

public class JsonLdContextDeserializer extends JsonDeserializer<JsonLdContext> {

    @Override
    public JsonLdContext deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            return new JsonLdContextString(p.getText());
        } else if (p.currentToken() == JsonToken.START_OBJECT) {
            return new JsonLdContextObject(p.readValueAs(java.util.Map.class));
        } else if (p.currentToken() == JsonToken.START_ARRAY) {
            List<String> list = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                list.add(p.getText());
            }
            return new JsonLdContextArray(list);
        }
        throw new IOException("Cannot deserialize JsonLdContext from token " + p.currentToken());
    }
}