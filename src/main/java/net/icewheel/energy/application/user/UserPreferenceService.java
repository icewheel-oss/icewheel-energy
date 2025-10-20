package net.icewheel.energy.application.user;

import java.util.Optional;

import net.icewheel.energy.application.user.model.UserPreference;

public interface UserPreferenceService {

    Optional<UserPreference> getCurrentUserPreference();

    UserPreference updateCurrentUserPreference(UserPreference userPreference);

}
