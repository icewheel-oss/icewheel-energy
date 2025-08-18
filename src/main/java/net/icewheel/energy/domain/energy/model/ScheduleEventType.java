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

package net.icewheel.energy.domain.energy.model;

/**
 * Defines the action to be taken when a schedule trigger fires.
 */
public enum ScheduleEventType {
    /** Set a low backup reserve to encourage discharging from the battery (on-peak). */
    START_DISCHARGE,
    /** Set a high backup reserve to encourage charging from the grid (off-peak). */
    START_CHARGE
}