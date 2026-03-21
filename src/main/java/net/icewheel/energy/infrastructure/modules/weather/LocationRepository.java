package net.icewheel.energy.infrastructure.modules.weather;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByLatitudeAndLongitude(double latitude, double longitude);
}
