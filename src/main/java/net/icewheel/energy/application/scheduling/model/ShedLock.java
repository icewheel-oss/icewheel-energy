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

package net.icewheel.energy.application.scheduling.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Why: This JPA entity represents the 'shedlock' table, allowing Hibernate to manage its creation and updates
 * automatically via the `spring.jpa.hibernate.ddl-auto` property. This is more robust than relying on
 * `schema.sql` scripts, which can fail in production environments due to permissions or database vendor differences.
 */
@Entity
@Table(name = "shedlock")
@Getter
@Setter
public class ShedLock {

	@Id
	@Column(name = "name", length = 64)
	private String name;

	@Column(name = "lock_until", nullable = false)
	private Instant lockUntil;

	@Column(name = "locked_at", nullable = false)
	private Instant lockedAt;

	@Column(name = "locked_by", length = 255, nullable = false)
	private String lockedBy;
}