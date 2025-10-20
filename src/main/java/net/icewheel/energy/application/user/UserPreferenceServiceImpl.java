package net.icewheel.energy.application.user;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.model.UserPreference;
import net.icewheel.energy.application.user.repository.UserPreferenceRepository;
import net.icewheel.energy.infrastructure.modules.location.zip.model.ZipCodeCache;
import net.icewheel.energy.infrastructure.modules.location.zip.service.ZipCodeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private final AppUserService appUserService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ZipCodeService zipCodeService;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserPreference> getCurrentUserPreference() {
        return appUserService.getCurrentUser().map(User::getPreference);
    }

    @Override
    @Transactional
    public UserPreference updateCurrentUserPreference(UserPreference userPreference) {
        return appUserService.getCurrentUser().map(user -> {
            UserPreference preference = Optional.ofNullable(user.getPreference()).orElseGet(() -> {
                UserPreference newPreference = new UserPreference();
                newPreference.setUser(user);
                user.setPreference(newPreference);
                return newPreference;
            });

            if (userPreference.getZipCode() != null && !userPreference.getZipCode().isBlank()) {
                zipCodeService.getCoordinates(userPreference.getZipCode()).ifPresent(zipCodeCache -> {
                    if (!zipCodeCache.isEmpty()) {
                        ZipCodeCache data = zipCodeCache.get(0); // Use the first result
                        
                        preference.setLocationName(data.getPlaceName());
                    }
                });
            }
            if (userPreference.getUnitPreference() != null) {
                preference.setUnitPreference(userPreference.getUnitPreference());
            }
            if (userPreference.getLocationName() != null) {
                preference.setLocationName(userPreference.getLocationName());
            }
            if (userPreference.getTimezone() != null) {
                preference.setTimezone(userPreference.getTimezone());
            }

            return userPreferenceRepository.save(preference);
        }).orElse(null);
    }
}
