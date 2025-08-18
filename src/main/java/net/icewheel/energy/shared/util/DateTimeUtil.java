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

package net.icewheel.energy.shared.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.zone.ZoneRulesException;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for handling date and time formatting.
 */
@Slf4j
public class DateTimeUtil {

    /**
     * Formats a Unix timestamp (in seconds) into a human-readable string for a given timezone.
     */
    public String formatTimestamp(long timestamp, String timezone) {
        if (timestamp == 0) {
            return "N/A";
        }
        return formatInstant(Instant.ofEpochSecond(timestamp), timezone);
    }

    /**
     * Formats an Instant into a human-readable string for a given timezone.
     */
    public String formatInstant(Instant instant, String timezone) {
        if (instant == null) {
            return "N/A";
        }
        try {
			// Why: Using a localized format is more user-friendly than a fixed pattern.
			// It adapts to locale-specific conventions for date and time representation.
			DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
					.withLocale(Locale.US) // Using US locale as a standard for consistency.
					.withZone(ZoneId.of(timezone));
			return formatter.format(instant);
        } catch (ZoneRulesException e) {
            log.warn("Invalid timezone provided: '{}'. Falling back to UTC.", timezone);
            // Fallback to UTC if the provided timezone is invalid
			return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.of("UTC")).format(instant);
        } catch (Exception e) {
            log.error("Error formatting date for instant: {}", instant, e);
            return "Invalid Date";
        }
    }

	/**
	 * Formats a duration in seconds into a human-readable string (e.g., "3h 20m" or "20m 0s").
	 * @param totalSeconds The total duration in seconds.
	 * @return A formatted string.
	 */
	public String formatDuration(long totalSeconds) {
		if (totalSeconds < 0) {
			return "N/A";
		}
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0) {
			return String.format("%dh %dm", hours, minutes);
		}
		else {
			return String.format("%dm %ds", minutes, seconds);
		}
	}

	/**
	 * Formats a Unix epoch timestamp (in seconds) into a human-readable date/time string for a given timezone.
	 * @param epochSecond The timestamp in seconds since the epoch.
	 * @param zoneId The IANA timezone ID.
	 * @return A formatted string.
	 */
	public String formatEpochSecond(long epochSecond, String zoneId) {
		return formatInstant(Instant.ofEpochSecond(epochSecond), zoneId);
	}
}
