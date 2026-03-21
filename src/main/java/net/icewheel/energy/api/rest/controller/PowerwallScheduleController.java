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

package net.icewheel.energy.api.rest.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.icewheel.energy.api.rest.dto.ScheduleRequest;
import net.icewheel.energy.api.rest.dto.ScheduleResponse;
import net.icewheel.energy.api.rest.dto.ScheduleToggleRequest;
import net.icewheel.energy.application.scheduling.PowerwallScheduleService;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaUserService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// Why: Explicitly produce JSON to enforce consistent API responses for clients.
@RequestMapping(value = "/api/schedules", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
// Why: Enable validation on method parameters and request bodies for safer inputs.
@Validated
@PreAuthorize("isAuthenticated()")
public class PowerwallScheduleController {

    private final PowerwallScheduleService scheduleService;
    private final TeslaUserService teslaUserService;

    // Why: Require JSON request bodies to avoid accidental form submissions and enforce a strict API contract.
    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody ScheduleRequest request, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = teslaUserService.findOrCreateUser(oauth2User);
        ScheduleResponse createdSchedule = scheduleService.createSchedulePeriod(request, user);
        return ResponseEntity.created(URI.create("/api/schedules/" + createdSchedule.getScheduleGroupId())).body(createdSchedule);
    }

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> getUserSchedules(@AuthenticationPrincipal OAuth2User oauth2User) {
        User user = teslaUserService.findOrCreateUser(oauth2User);
        return ResponseEntity.ok(scheduleService.findSchedulesByUser(user));
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ScheduleResponse> getScheduleById(@PathVariable UUID scheduleId, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = teslaUserService.findOrCreateUser(oauth2User);
        return scheduleService.findScheduleByGroupId(scheduleId, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Why: Require JSON body for updates to enforce strict content type handling.
    @PutMapping(value = "/{scheduleId}", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScheduleResponse> updateSchedule(@PathVariable UUID scheduleId, @Valid @RequestBody ScheduleRequest request, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = teslaUserService.findOrCreateUser(oauth2User);
        ScheduleResponse updatedSchedule = scheduleService.updateSchedulePeriod(scheduleId, request, user);
        return ResponseEntity.ok(updatedSchedule);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID scheduleId, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = teslaUserService.findOrCreateUser(oauth2User);
        scheduleService.deleteSchedulePeriod(scheduleId, user);
        return ResponseEntity.noContent().build();
    }

    // Why: Require JSON body for partial updates to enforce strict content type and avoid CSRF-prone form toggles.
    @PatchMapping(value = "/{scheduleId}/toggle", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> toggleSchedule(@PathVariable UUID scheduleId, @Valid @RequestBody ScheduleToggleRequest request, @AuthenticationPrincipal OAuth2User oauth2User) {
        User user = teslaUserService.findOrCreateUser(oauth2User);
        scheduleService.updateScheduleEnabledStatus(scheduleId, request.getEnabled(), user);
        return ResponseEntity.noContent().build();
    }
}