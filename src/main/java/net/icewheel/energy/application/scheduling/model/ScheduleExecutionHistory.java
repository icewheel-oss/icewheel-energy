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
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "schedule_execution_history")
@Getter
@Setter
public class ScheduleExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID scheduleId;

    @Column(nullable = false)
    private UUID scheduleGroupId;

    @Column(nullable = false)
    private String scheduleName;

	@Column(nullable = false)
	private String userId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant executionTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
	@Check(constraints = "status IN ('SUCCESS', 'FAILURE', 'SKIPPED')")
    private ExecutionStatus status;

    @Column(length = 1024)
    private String details;

    @Column(length = 255)
    private String cronExpression;

    @Column(length = 255)
    private String cronDescription;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'REGULAR'")
	@Check(constraints = "execution_type IN ('REGULAR', 'RECONCILIATION_CONTINUOUS', 'RECONCILIATION_STARTUP', 'WEATHER_EVALUATION')")
	private ExecutionType executionType;

	public enum ExecutionType {
		REGULAR, // A normal, scheduled execution via Quartz
		RECONCILIATION_CONTINUOUS, // A periodic state correction check
		RECONCILIATION_STARTUP, // A one-time state correction check on application startup
		WEATHER_EVALUATION // A weather evaluation task

	}

    public enum ExecutionStatus {
        SUCCESS,
		FAILURE,
		SKIPPED
    }

    // Why: JPA-safe equality based on identifier; supports Hibernate proxies and avoids mutable field comparison.
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) o).getHibernateLazyInitializer()
                .getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ScheduleExecutionHistory that = (ScheduleExecutionHistory) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass()
                .hashCode() : getClass().hashCode();
    }
}