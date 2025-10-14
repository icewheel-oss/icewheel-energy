package net.icewheel.energy.infrastructure.modules.weather.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Represents an audit event related to the weather-aware scheduling feature.
 * These events are used to track the decisions and actions taken by the system,
 * which is useful for debugging and for providing a history to the user.
 */
@Entity
@Table(name = "weather_audit_events")
@Getter
@Setter
public class WeatherAuditEvent {

    /**
     * The unique identifier for this audit event.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The ID of the user associated with this event.
     */
    @Column(nullable = false)
    private String userId;

    /**
     * The ID of the energy site associated with this event.
     */
    @Column(nullable = false)
    private String siteId;

    /**
     * The timestamp when the event occurred.
     */
    @Column(nullable = false)
    private Instant eventTimestamp;

    /**
     * The type of the audit event.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Check(constraints = "eventType IN ('FORECAST_FETCH_SUCCESS', 'FORECAST_FETCH_FAILURE', 'EVALUATION_GOOD_WEATHER', 'EVALUATION_BAD_WEATHER', 'FORCED_CHARGE_INITIATED', 'FORCED_CHARGE_SKIPPED', 'EVALUATION_FAILURE', 'EVALUATION_SUCCESS')")
    private WeatherAuditEventType eventType;

    /**
     * A detailed description of the event.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String details;
}