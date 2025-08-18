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

package net.icewheel.energy.application.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * A utility component to convert Java objects to a pretty-printed JSON string for display in the UI.
 * This is particularly useful for rendering unmapped properties from API responses.
 */
@Component("jsonUtil")
@Slf4j
public class JsonUtil {

	private final ObjectMapper objectMapper;

	public JsonUtil() {
		this.objectMapper = new ObjectMapper()
				.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()) // For Instant, etc.
				.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
				.enable(SerializationFeature.INDENT_OUTPUT);
	}

	public String toJson(Object obj) {
		try {
			return obj != null ? objectMapper.writeValueAsString(obj) : "{}";
		}
		catch (JsonProcessingException e) {
			log.error("Error converting object to JSON string for UI display", e);
			return "{\n  \"error\": \"Could not render object as JSON.\"\n}";
		}
	}
}