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

package net.icewheel.energy.infrastructure.vendors.tesla.auth.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.shared.model.Auditable;

/**
 * Represents a Tesla OAuth token stored in the database.
 */
@Entity
@Table(name = "tokens")
@Getter
@Setter
public class Token extends Auditable implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The user ID associated with this token.
     */
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

	/**
	 * The user associated with this token.
	 */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    /**
     * The email address associated with the Tesla account for this token.
     */
    private String email;
    /**
     * The name associated with the Tesla account for this token.
     */
    private String name;

    /**
     * The access token used to authenticate with the Tesla API.
     */
    @Column(columnDefinition = "TEXT")
    private String accessToken;

    /**
     * The refresh token used to obtain a new access token.
     */
    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    /**
     * The exact timestamp when the access token will expire, represented as the number of seconds since the Unix epoch.
     */
    private Long expiration;

    /**
     * The exact timestamp when the refresh token will expire, represented as the number of seconds since the Unix epoch.
     */
    private Long refreshTokenExpiration;

    /**
     * The type of token, which is typically "Bearer".
     */
    private String tokenType;

    /**
     * The scope of the access token.
     */
    private String scope;

	/**
	 * Returns the expiration time as an {@link Instant}.
	 * This is a convenience method that converts the 'expiration' field (epoch seconds) into an Instant.
	 *
	 * @return The expiration instant, or {@link Instant#EPOCH} if the expiration is not set.
	 */
	public Instant getExpiresAt() {
		return (this.expiration != null && this.expiration > 0) ? Instant.ofEpochSecond(this.expiration) : Instant.EPOCH;
	}

    // Why: JPA-safe equality based on immutable identifier to work with Hibernate proxies and avoid using mutable fields.
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) o).getHibernateLazyInitializer()
                .getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Token that = (Token) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass()
                .hashCode() : getClass().hashCode();
    }
}