package net.icewheel.energy.infrastructure.modules.weather;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HourlyPredictionRepository extends JpaRepository<HourlyPrediction, UUID> {
}
