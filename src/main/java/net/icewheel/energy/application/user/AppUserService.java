package net.icewheel.energy.application.user;

import java.util.Optional;

import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.model.UserPreference;

/**
 * A service for user-related operations.
 */
public interface AppUserService {

    /**
     * Gets the currently authenticated user.
     * @return An optional containing the current user, or empty if the user is not authenticated.
     */
    Optional<User> getCurrentUser();

    /**
     * Gets the preferences of the currently authenticated user.
     * @return An optional containing the current user's preferences, or empty if the user is not authenticated.
     */
    UserPreference getCurrentUserPreference();

    /**
     * Saves the given user preference.
     * @param preferences The user preference to save.
     */
    void save(UserPreference preferences);
}
