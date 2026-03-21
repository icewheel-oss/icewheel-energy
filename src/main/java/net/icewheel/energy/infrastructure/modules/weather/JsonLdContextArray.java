package net.icewheel.energy.infrastructure.modules.weather;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

public class JsonLdContextArray extends net.icewheel.energy.infrastructure.weather.nws.gen.model.JsonLdContext {

    private final List<String> values;

    public JsonLdContextArray(List<String> values) {
        this.values = values;
    }

    @JsonValue
    public List<String> getValues() {
        return values;
    }
}
