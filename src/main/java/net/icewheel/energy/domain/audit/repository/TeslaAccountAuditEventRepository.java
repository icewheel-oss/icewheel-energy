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

package net.icewheel.energy.domain.audit.repository;

import java.util.Optional;
import java.util.UUID;

import net.icewheel.energy.domain.audit.model.TeslaAccountAuditEvent;
import net.icewheel.energy.domain.auth.model.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeslaAccountAuditEventRepository extends JpaRepository<TeslaAccountAuditEvent, UUID> {
    Optional<TeslaAccountAuditEvent> findTopByUserOrderByTimestampDesc(User user);
}
