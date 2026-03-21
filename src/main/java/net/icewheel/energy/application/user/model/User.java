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

package net.icewheel.energy.application.user.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.icewheel.energy.application.scheduling.model.PowerwallSchedule;
import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderCredential;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.Token;
import net.icewheel.energy.shared.model.Auditable;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends Auditable implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;
	/**
	 * The name associated with the Single Sign On Provider account, google in this case.
	 */
    private String name;
	/**
	 * The email associated with the Single Sign On Provider account, google in this case.
	 */
	@Column(unique = true)
	private String email;

	/**
	 * The list of tokens associated with this user.
	 */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Token> tokens = new ArrayList<>();

	/**
	 * Stores the full user profile from the OAuth2 provider as a JSON object.
	 * This provides flexibility to access new profile attributes without changing the database schema.
	 * Mapped to a jsonb column in PostgreSQL.
	 */
	@Column(name = "profile_attributes", columnDefinition = "TEXT")
	private String profileAttributes;

	@Transient
	private Map<String, Object> profileAttributesMap;

	public Map<String, Object> getProfileAttributes() {
		if (this.profileAttributesMap == null && this.profileAttributes != null) {
			try {
				com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
				mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
				this.profileAttributesMap = mapper.readValue(this.profileAttributes, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
			} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}
		return this.profileAttributesMap;
	}

	public void setProfileAttributes(Map<String, Object> profileAttributes) {
		this.profileAttributesMap = profileAttributes;
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
			this.profileAttributes = mapper.writeValueAsString(profileAttributes);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PowerwallSchedule> powerwallSchedules = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private List<WeatherProviderCredential> weatherProviderCredentials = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserPreference preference;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<UserZipCode> zipCodes = new ArrayList<>();

    

    // Why: JPA-safe equality based on identifier to avoid issues with proxies and mutable fields.
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) o).getHibernateLazyInitializer()
                .getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        User that = (User) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy ? ((org.hibernate.proxy.HibernateProxy) this).getHibernateLazyInitializer()
                .getPersistentClass()
                .hashCode() : getClass().hashCode();
    }
}
