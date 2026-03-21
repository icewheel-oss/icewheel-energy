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

package net.icewheel.energy.infrastructure.modules.weather;

import com.fasterxml.jackson.annotation.JsonValue;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.ISO8601Interval;


/**
 * A concrete implementation of the {@link ISO8601Interval} interface.
 * This class acts as a simple wrapper for the raw string value of the interval,
 * allowing Jackson to deserialize it correctly.
 */
public record ISO8601IntervalString(String value) implements ISO8601Interval {

	@JsonValue
	@Override
	public String value() {
		return value;
	}

	@Override
	public String toString() {
		// Override the default record toString() to return the raw value for cleaner logging.
		return value;
	}
}