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

package net.icewheel.energy.shared.converter;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class LenientDayOfWeekConverter implements AttributeConverter<DayOfWeek, String> {

	private static final Logger log = LoggerFactory.getLogger(LenientDayOfWeekConverter.class);

	@Override
	public String convertToDatabaseColumn(DayOfWeek attribute) {
		if (attribute == null) {
			return null;
		}
		// Always store as the standard enum name
		return attribute.name();
	}

	@Override
	public DayOfWeek convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}

		// Try parsing as a standard enum name (e.g., "MONDAY")
		try {
			return DayOfWeek.valueOf(dbData.toUpperCase());
		} catch (IllegalArgumentException e) {
			// Not a valid name, fall through to try parsing as a number
		}

		// Try parsing as a number (e.g., "5" for Friday) which might be legacy data
		try {
			int dayValue = Integer.parseInt(dbData);
			return DayOfWeek.of(dayValue);
		} catch (Exception e) {
			log.error("Failed to convert invalid database value '{}' to a DayOfWeek. This value will be ignored.", dbData, e);
			return null; // Return null for un-parseable values to prevent crashing
		}
	}
}