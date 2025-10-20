package net.icewheel.energy.infrastructure.modules.weather.model;

import java.time.Instant;
import java.util.UUID;

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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.icewheel.energy.application.user.model.User;

/**
 * Represents a user's credential for a specific weather provider.
 *
 * <p>This entity stores the API key and secret (if any) for a weather service, and
 * allows the user to enable or disable it. It also stores the priority of the
 * provider in the provider chain, and the daily quota for the provider.
 */
@ToString
@Entity
@Table(name = "weather_provider_credentials")
@Getter
@Setter
public class WeatherProviderCredential {

    /**
     * The unique identifier for this credential.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user who owns this credential.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
	@ToString.Exclude
	private User user;

    /**
     * The type of the weather provider (e.g., OPEN_WEATHER_MAP).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WeatherProviderType providerType;

    /**
     * A user-friendly label for this credential (e.g., "My Home NWS Key").
     */
    @Column(nullable = false)
    private String label;

    /**
     * The API key for the weather provider. This field is encrypted at rest.
     */
    @Column(nullable = true)

    private String apiKey;

    /**
     * The API secret for the weather provider (if applicable). This field is encrypted at rest.
     */
    @Column(columnDefinition = "TEXT")

    private String apiSecret;

    /**
     * The User-Agent string to be used for the NWS provider.
     */
    @Column(columnDefinition = "TEXT")
    private String userAgent;

    /**
     * Whether this credential is currently enabled and can be used to fetch weather data.
     */
    @Column(nullable = false)
    private boolean enabled;

    /**
     * The number of API calls allowed per day for this credential.
     */
    @Column
    private Integer dailyQuota;

    /**
     * The priority of this provider in the provider chain.
     */
    @Column
    private Integer priority;

    /**
     * If this credential has been temporarily disabled due to errors, this field will contain the timestamp until which it is disabled.
     */
    @Column
    private Instant disabledUntil;
}