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

import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.Keypair;
import net.icewheel.energy.infrastructure.vendors.tesla.repository.KeypairRepository;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
// Why: This test uses the h2 profile, which is configured in application-h2.yml.
// Why: This property source overrides the datasource URL for test isolation.
// The INIT=RUNSCRIPT parameter is the most reliable way to execute the schema.sql script.
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb_keypair;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=RUNSCRIPT FROM 'classpath:schema.sql'",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class H2KeypairRepositoryIT {

    @Autowired
    private KeypairRepository keypairRepository;

    @Test
    void shouldCreateAndReadKeypair_onH2() {
        // Arrange
        Keypair kp = new Keypair();
        kp.setId("default");
        kp.setPublicKey(new byte[]{1, 2, 3});
        kp.setPrivateKey(new byte[]{4, 5, 6});

        // Act
        keypairRepository.save(kp);

        // Assert
        assertThat(keypairRepository.findById("default")).isPresent();
        Keypair loaded = keypairRepository.findById("default").orElseThrow();
        assertThat(loaded.getPublicKey()).containsExactly(1, 2, 3);
        assertThat(loaded.getPrivateKey()).containsExactly(4, 5, 6);
    }
}
