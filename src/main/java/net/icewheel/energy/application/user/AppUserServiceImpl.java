package net.icewheel.energy.application.user;

import java.util.Optional;

import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.model.UserPreference;
import net.icewheel.energy.application.user.repository.UserPreferenceRepository;
import net.icewheel.energy.application.user.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * The default implementation of the {@link AppUserService}.
 */
@Service
public class AppUserServiceImpl implements AppUserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public AppUserServiceImpl(UserRepository userRepository, UserPreferenceRepository userPreferenceRepository) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> getCurrentUser() {
        // Why: We get the principal from the security context to identify the current user.
        // This is the standard way to get the current user in Spring Security.
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            String email = oauth2User.getAttribute("email");
            return userRepository.findByEmail(email);
        }

        return Optional.empty();
    }

    @Override
    public UserPreference getCurrentUserPreference() {
        return getCurrentUser().map(User::getPreference).orElse(null);
    }

    @Override
    public void save(UserPreference preferences) {
        userPreferenceRepository.save(preferences);
    }
}
