package net.icewheel.energy.infrastructure.modules.weather;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "locations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"latitude", "longitude"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private double latitude;

    private double longitude;

    @Column(name = "grid_id")
    private String gridId;

    @Column(name = "grid_x")
    private int gridX;

    @Column(name = "grid_y")
    private int gridY;

    @Column(name = "forecast_url")
    private String forecastUrl;
}
