package net.icewheel.energy.infrastructure.modules.weather;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "forecast_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false)
    private Instant fetchedAt;

	@Lob
	@Column(name = "raw_hourly_response", columnDefinition = "TEXT")
	private String rawHourlyResponse;

	@Lob
	@Column(name = "raw_daily_response", columnDefinition = "TEXT")
	private String rawDailyResponse;

	@Lob
	@Column(name = "raw_points_response", columnDefinition = "TEXT")
	private String rawPointsResponse;

	@Lob
	@Column(name = "raw_grid_data_response", columnDefinition = "TEXT")
	private String rawGridDataResponse;

	@Lob
	@Column(name = "raw_observation_stations_response", columnDefinition = "TEXT")
	private String rawObservationStationsResponse;

	@Lob
	@Column(name = "raw_observation_json", columnDefinition = "TEXT")
	private String rawObservationJson;

	@OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HourlyPrediction> hourlyPredictions;
}
