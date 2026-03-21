package net.icewheel.energy.infrastructure.modules.weather;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ForecastReportRepository extends JpaRepository<ForecastReport, UUID> {
    Optional<ForecastReport> findFirstByLocation_IdOrderByFetchedAtDesc(UUID locationId);

    Optional<ForecastReport> findFirstByLocation_LatitudeAndLocation_LongitudeOrderByFetchedAtDesc(double latitude, double longitude);
}
