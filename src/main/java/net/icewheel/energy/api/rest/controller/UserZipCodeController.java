package net.icewheel.energy.api.rest.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.icewheel.energy.application.user.UserZipCodeService;
import net.icewheel.energy.application.user.dto.UserZipCodeDto;
import net.icewheel.energy.application.user.model.UserZipCode;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rest/user/zip-codes")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserZipCodeController {

    private final UserZipCodeService userZipCodeService;

    @GetMapping
    public ResponseEntity<List<UserZipCode>> getZipCodes() {
        return ResponseEntity.ok(userZipCodeService.getZipCodesForCurrentUser());
    }

    @PostMapping
    public ResponseEntity<UserZipCode> addZipCode(@Valid @RequestBody UserZipCodeDto zipCodeDto) {
        return ResponseEntity.ok(userZipCodeService.addZipCodeToCurrentUser(zipCodeDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteZipCode(@PathVariable Long id) {
        userZipCodeService.deleteZipCode(id);
        return ResponseEntity.ok().build();
    }
}
