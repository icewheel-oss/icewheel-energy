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
import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.repository.energy.PowerwallScheduleRepository;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PowerwallScheduleRepository powerwallScheduleRepository;

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
}
