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
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.PointRelativeLocation;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.RelativeLocationGeoJson;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.RelativeLocationJsonLd;

public class PointRelativeLocationDeserializer extends JsonDeserializer<PointRelativeLocation> {
	@Override
	public PointRelativeLocation deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		ObjectCodec codec = p.getCodec();
		JsonNode node = codec.readTree(p);

		// The JSON-LD variant has a "geometry" property, while the GeoJSON variant has a "type" property.
		if (node.has("geometry")) {
			return codec.treeToValue(node, RelativeLocationJsonLd.class);
		} else if (node.has("type")) {
			return codec.treeToValue(node, RelativeLocationGeoJson.class);
		}

		throw new IOException("Cannot determine subtype of PointRelativeLocation from JSON: " + node);
	}
}