package net.icewheel.energy.application.user.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a user's preferences.
 *
 * <p>This entity stores user-specific settings, such as location, unit preferences,
 * and whether forced charging is active. These preferences are used to personalize the
 * user's experience and to control the behavior of the weather-aware scheduling
 * feature.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
public class UserPreference {

    /**
     * The unique identifier for the user preference.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user associated with these preferences.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore // Add this annotation
    private User user;

    

    /**
     * The user's zip code, which can also be used for location.
     */
    @Column
    private String zipCode;

    /**
     * The user's preferred units (e.g., Fahrenheit or Celsius).
     */
    @Column
    private String unitPreference;

    /**
     * A user-friendly name for the location.
     */
    @Column
    private String locationName;

    /**
     * A flag to indicate if forced charging is active.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isForcedChargingActive = false;

    /**
     * The user's timezone for weather forecasts.
     */
    @Column
    private String timezone;
}
