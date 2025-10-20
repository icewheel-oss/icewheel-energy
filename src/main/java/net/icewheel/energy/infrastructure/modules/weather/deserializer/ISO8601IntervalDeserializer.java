/*
 * IceWheel Energy
 * Copyright (C) 2025 IceWheel LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package net.icewheel.energy.infrastructure.modules.weather.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import net.icewheel.energy.infrastructure.modules.weather.ISO8601IntervalString;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.ISO8601Interval;

/**
 * Custom Jackson deserializer for the {@link ISO8601Interval} interface.
 * The NWS API represents this as a string, but the OpenAPI generator creates an
 * abstract interface. This deserializer reads the string value and wraps it in a
 * concrete {@link ISO8601IntervalString} implementation.
 */
public class ISO8601IntervalDeserializer extends JsonDeserializer<ISO8601Interval> {
	@Override
	public ISO8601Interval deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String text = p.getText();
		if (text == null || text.isBlank()) {
			return null;
		}
		return new ISO8601IntervalString(text);
	}
}