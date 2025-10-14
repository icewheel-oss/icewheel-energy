package net.icewheel.energy.infrastructure.modules.weather.repository;

import java.util.UUID;

import net.icewheel.energy.infrastructure.modules.weather.model.WeatherAuditEvent;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * A Spring Data JPA repository for {@link WeatherAuditEvent} entities.
 */
public interface WeatherAuditEventRepository extends JpaRepository<WeatherAuditEvent, UUID> {
}