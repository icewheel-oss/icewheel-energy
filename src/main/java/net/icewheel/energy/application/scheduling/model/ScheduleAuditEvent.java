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
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.icewheel.energy.application.user.model.User;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "schedule_audit_events")
@Getter
@Setter
public class ScheduleAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID scheduleGroupId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private String scheduleName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Check(constraints = "action IN ('CREATED', 'UPDATED', 'DELETED', 'RECONCILED', 'WEATHER_UPDATE')")
    private AuditAction action;
    @Column(name = "details", columnDefinition = "TEXT")
	private String details;

	@Transient
	private Map<String, Object> detailsMap;

	public Map<String, Object> getDetails() {
		if (this.detailsMap == null && this.details != null) {
			try {
				com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
				mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
				this.detailsMap = mapper.readValue(this.details, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
			} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}
		return this.detailsMap;
	}

	public void setDetails(Map<String, Object> details) {
		this.detailsMap = details;
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
			this.details = mapper.writeValueAsString(details);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public enum AuditAction {
        CREATED,
        UPDATED,
		DELETED,
		RECONCILED,
		/** A change made automatically by the weather-aware scheduling service. */
		WEATHER_UPDATE
    }

    // Why: JPA-safe equality based on identifier; handles Hibernate proxies and avoids using mutable fields.
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) o).getHibernateLazyInitializer()
                .getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ScheduleAuditEvent that = (ScheduleAuditEvent) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass()
                .hashCode() : getClass().hashCode();
    }
}