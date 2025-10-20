package net.icewheel.energy.infrastructure.modules.location.zip.repository;

import java.util.List;

import net.icewheel.energy.infrastructure.modules.location.zip.model.ZipCodeCache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZipCodeCacheRepository extends JpaRepository<ZipCodeCache, Long> {
    List<ZipCodeCache> findByZipCode(String zipCode);

    List<ZipCodeCache> findByZipCodeAndActiveTrue(String zipCode);
}