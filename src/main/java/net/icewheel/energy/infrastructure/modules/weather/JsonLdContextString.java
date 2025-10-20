package net.icewheel.energy.infrastructure.modules.weather;

import com.fasterxml.jackson.annotation.JsonValue;

public class JsonLdContextString extends net.icewheel.energy.infrastructure.weather.nws.gen.model.JsonLdContext {
    private final String value;

    public JsonLdContextString(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
