package net.icewheel.energy.infrastructure.modules.weather;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

public class JsonLdContextObject extends net.icewheel.energy.infrastructure.weather.nws.gen.model.JsonLdContext {
    private final Map<String, Object> value;

    public JsonLdContextObject(Map<String, Object> value) {
        this.value = value;
    }

    @JsonValue
    public Map<String, Object> getValue() {
        return value;
    }
}
