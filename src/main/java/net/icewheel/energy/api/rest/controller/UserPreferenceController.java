package net.icewheel.energy.api.rest.controller;

import lombok.RequiredArgsConstructor;
import net.icewheel.energy.application.user.UserPreferenceService;
import net.icewheel.energy.application.user.model.UserPreference;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rest/user")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping("/preferences")
    public ResponseEntity<UserPreference> getUserPreferences() {
        return userPreferenceService.getCurrentUserPreference()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/preferences")
    public ResponseEntity<UserPreference> updatePreferences(@RequestBody UserPreference preferenceUpdates) {
        UserPreference updatedPreference = userPreferenceService.updateCurrentUserPreference(preferenceUpdates);
        return ResponseEntity.ok(updatedPreference);
    }
}
