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

-- ShedLock Table for Distributed Locking of Scheduled Tasks
-- Why: This table is used by the ShedLock library to manage distributed locks for scheduled tasks.
-- It is created here manually because we are not using a ShedLock JPA provider.
-- CREATE TABLE IF NOT EXISTS shedlock
-- (
--     name       VARCHAR(64)  NOT NULL PRIMARY KEY,
--     lock_until TIMESTAMP    NOT NULL,
--     locked_at  TIMESTAMP    NOT NULL,
--     locked_by  VARCHAR(255) NOT NULL
-- );