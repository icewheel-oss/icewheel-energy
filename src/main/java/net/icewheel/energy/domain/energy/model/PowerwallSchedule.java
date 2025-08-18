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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.shared.model.Auditable;
import net.icewheel.energy.shared.converter.LenientDayOfWeekConverter;

@Entity
@Table(name = "powerwall_schedules")
@Getter
@Setter
public class PowerwallSchedule extends Auditable {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID scheduleGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String energySiteId;

    @ElementCollection(targetClass = DayOfWeek.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "powerwall_schedule_days", joinColumns = @JoinColumn(name = "schedule_id"))
    @Convert(converter = LenientDayOfWeekConverter.class)
    @Column(name = "day_of_week", nullable = false)
    private Set<DayOfWeek> daysOfWeek;

    @Column(nullable = false)
    private ZoneId timeZone;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleEventType eventType;

    @Column(nullable = false)
    private LocalTime scheduledTime;

    @Column(nullable = false)
    private int backupPercent;

    @Column(nullable = false)
    private String cronExpression;

	@Column
	private LocalTime validFromTime;

	@Column
	private LocalTime validToTime;

	/**
	 * This gives users control over how reconciliation works for their schedule.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(255) default 'CONTINUOUS'")
	private ReconciliationMode reconciliationMode = ReconciliationMode.CONTINUOUS;

	/**
	 * JPA-safe equality check based on the entity's unique identifier.
	 * This approach avoids issues with Hibernate proxies and mutable fields that can occur with default implementations.
	 */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) o).getHibernateLazyInitializer()
                .getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        PowerwallSchedule that = (PowerwallSchedule) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass()
                .hashCode() : getClass().hashCode();
    }
}