package net.icewheel.energy.application.user.repository;

import java.util.UUID;

import net.icewheel.energy.application.user.model.UserPreference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

}
