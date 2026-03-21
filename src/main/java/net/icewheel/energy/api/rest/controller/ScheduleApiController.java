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

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.api.rest.dto.ScheduleRequest;
import net.icewheel.energy.application.scheduling.ImportResult;
import net.icewheel.energy.application.scheduling.PowerwallScheduleService;
import net.icewheel.energy.application.scheduling.exception.ScheduleImportException;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaUserService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ScheduleApiController {

	private final PowerwallScheduleService scheduleService;
	private final TeslaUserService teslaUserService;
	private final ObjectMapper objectMapper;

	@GetMapping("/export")
	public ResponseEntity<List<ScheduleRequest>> exportSchedules(@AuthenticationPrincipal OAuth2User principal) {
		User user = teslaUserService.findOrCreateUser(principal);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedules.json\"")
				.body(scheduleService.getSchedulesForExport(user));
	}

	@PostMapping("/import")
	public ResponseEntity<String> importSchedules(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal OAuth2User principal) {
		User user = teslaUserService.findOrCreateUser(principal);

		if (file.isEmpty()) {
			return ResponseEntity.badRequest().body("Import failed: Please select a file to upload.");
		}

		// Why: Security check to ensure only JSON files are processed, preventing other file types from being uploaded.
		if (!MediaType.APPLICATION_JSON_VALUE.equals(file.getContentType())) {
			return ResponseEntity.badRequest().body("Import failed: Invalid file type. Please upload a JSON file.");
		}

		try {
			List<ScheduleRequest> schedulesToImport = objectMapper.readValue(file.getInputStream(), new TypeReference<>() { });
			ImportResult result = scheduleService.importSchedules(schedulesToImport, user);

			// Build a user-friendly response message based on the import result.
			StringBuilder responseMessage = new StringBuilder();
			if (result.getImportedCount() > 0) {
				responseMessage.append("Successfully imported ").append(result.getImportedCount())
						.append(result.getImportedCount() > 1 ? " schedules. " : " schedule. ");
			}
			else {
				responseMessage.append("Import finished. No new schedules were imported. ");
			}

			if (!result.getSkippedDuplicateNames().isEmpty()) {
				responseMessage.append(result.getSkippedDuplicateNames().size())
						.append(result.getSkippedDuplicateNames().size() > 1 ? " schedules" : " schedule")
						.append(" were skipped due to duplicate names: ")
						.append(String.join(", ", result.getSkippedDuplicateNames())).append(".");
			}

			if (!result.getSkippedDuplicateContent().isEmpty()) {
				responseMessage.append(result.getSkippedDuplicateContent().size())
						.append(result.getSkippedDuplicateContent().size() > 1 ? " schedules" : " schedule")
						.append(" were skipped because their settings (time, days, etc.) were identical to existing schedules: ")
						.append(String.join(", ", result.getSkippedDuplicateContent())).append(".");
			}

			// Determine the best HTTP status code. CREATED if new items were added, OK otherwise.
			HttpStatus status = result.getImportedCount() > 0 ? HttpStatus.CREATED : HttpStatus.OK;

			return ResponseEntity.status(status).body(responseMessage.toString().trim());
		}
		catch (ScheduleImportException e) {
			// This catches validation errors from the service layer.
			return ResponseEntity.badRequest().body(e.getMessage());
		}
		catch (IOException e) {
			// This can happen if the file is corrupt or not valid JSON.
			return ResponseEntity.badRequest()
					.body("Import failed: Could not parse the JSON file. Please ensure it is well-formed.");
		}
	}
}