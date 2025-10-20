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

package net.icewheel.energy.application.scheduling;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the result of an import operation.
 */
@Getter
@RequiredArgsConstructor
public class ImportResult {
	private final int importedCount;
	private final List<String> skippedDuplicateNames;
	private final List<String> skippedDuplicateContent;
}