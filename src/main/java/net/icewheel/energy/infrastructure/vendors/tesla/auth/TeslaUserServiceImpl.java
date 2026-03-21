/*
 * IceWheel Energy
 * Copyright (C) 2025 IceWheel LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package net.icewheel.energy.infrastructure.vendors.tesla.auth;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.model.UserPreference;
import net.icewheel.energy.application.user.repository.UserRepository;
import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderCredential;
import net.icewheel.energy.infrastructure.modules.weather.model.WeatherProviderType;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeslaUserServiceImpl implements TeslaUserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User findOrCreateUser(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        return userRepository.findByEmail(email)
                .map(user -> {
                    // Update user attributes from SSO provider on each login
                    updateUserFromOAuth2User(user, oauth2User);
                    return userRepository.save(user);
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setId(oauth2User.getName());
                    newUser.setEmail(email);
                    updateUserFromOAuth2User(newUser, oauth2User);

                    // Create a new user preference object
                    UserPreference userPreference = new UserPreference();
                    userPreference.setUser(newUser);
                    newUser.setPreference(userPreference);

                    // Add default NWS weather provider credential
                    WeatherProviderCredential credential = new WeatherProviderCredential();
                    credential.setUser(newUser);
                    credential.setProviderType(WeatherProviderType.NWS);
                    credential.setLabel("Default NWS Provider");
                    credential.setApiKey("none"); // NWS does not require an API key
                    credential.setEnabled(true);
                    newUser.getWeatherProviderCredentials().add(credential);

                    return userRepository.save(newUser);
                });
    }

    @Override
    @Transactional
    public void disconnectTeslaAccount(User user) {
        user.getTokens().clear();
        user.getPowerwallSchedules().forEach(schedule -> schedule.setEnabled(false));
        userRepository.save(user);
    }

    private void updateUserFromOAuth2User(User user, OAuth2User oauth2User) {
        user.setName(oauth2User.getAttribute("name"));
        // Store all attributes from the OAuth2User in the profileAttributes map
        Map<String, Object> attributes = oauth2User.getAttributes();
        user.setProfileAttributes(attributes);
    }

    @Override
    @Transactional
    public void save(User user) {
        userRepository.save(user);
    }
}
