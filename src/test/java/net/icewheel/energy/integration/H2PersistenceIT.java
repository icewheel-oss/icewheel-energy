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

package net.icewheel.energy.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.icewheel.energy.domain.auth.model.User;
import net.icewheel.energy.domain.energy.model.ScheduleAuditEvent;
import net.icewheel.energy.infrastructure.repository.auth.UserRepository;
import net.icewheel.energy.infrastructure.repository.energy.ScheduleAuditEventRepository;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify H2 support end-to-end.
 *
 * Why this test:
 * - Boots the Spring context with the "h2" profile to exercise the H2 configuration.
 * - Uses an in-memory H2 database for isolation and speed in CI.
 * - Persists and reads entities that rely on PostgreSQL-specific columnDefinition (jsonb, uuid)
 *   to ensure our H2 setup remains compatible without changing domain entities.
 */
@SpringBootTest
// Why: This test uses the h2 profile, which is configured in application-h2.yml.
@ActiveProfiles("h2")
// Why: This property source is used to override the datasource URL to use an in-memory database for tests.
// This makes the tests independent of the local development database.
// The spring.sql.init.mode and spring.sql.init.schema-locations properties are used to ensure that the schema.sql script is executed before the tests run.
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.sql.init.mode=always",
		"spring.sql.init.schema-locations=classpath:schema.sql"
})
class H2PersistenceIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleAuditEventRepository auditRepository;

    @Test
    void shouldPersistAndQueryUserAndAuditEvent_onH2() {
        // Arrange: create a user with JSON profile attributes
        User user = new User();
        user.setId("user-h2-1");
        user.setName("H2 Tester");
        user.setEmail("h2.tester@example.com");
        Map<String, Object> profile = new HashMap<>();
        profile.put("locale", "en-US");
        profile.put("tz", "UTC");
        user.setProfileAttributes(profile);

        // Act: save user
        userRepository.save(user);

        // Assert: query by email
        Optional<User> found = userRepository.findByEmail("h2.tester@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getProfileAttributes())
                .isNotNull()
                .containsEntry("locale", "en-US");

        // Arrange: create audit event with JSON details
        ScheduleAuditEvent event = new ScheduleAuditEvent();
        event.setScheduleGroupId(UUID.randomUUID());
        event.setUser(found.get());
        event.setScheduleName("Test-Schedule");
        event.setAction(ScheduleAuditEvent.AuditAction.CREATED);
        Map<String, Object> details = Map.of("key", "value", "attempt", 1);
        event.setDetails(details);

        // Act: save audit event
        auditRepository.save(event);

        // Assert: query back by user ordered by timestamp desc
		Pageable pageable = PageRequest.of(0, 5, Sort.by("timestamp").descending());
		Page<ScheduleAuditEvent> eventsPage = auditRepository.findByUser(found.get(), pageable);
		assertThat(eventsPage.getContent()).isNotEmpty();
		assertThat(eventsPage.getContent().get(0).getDetails())
                .isNotNull()
                .containsEntry("key", "value");
    }
}
