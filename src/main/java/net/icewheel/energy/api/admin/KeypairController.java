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

package net.icewheel.energy.api.admin;

import lombok.RequiredArgsConstructor;
import net.icewheel.energy.infrastructure.security.KeypairService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to serve the application's public key at the well-known URL
 * required for Tesla Fleet API domain verification.
 */
@Controller
@RequiredArgsConstructor
public class KeypairController {

    private final KeypairService keypairService;

    // Why: Serve Tesla verification key with correct PEM media type at the required well-known path.
    @GetMapping(value = "/.well-known/appspecific/com.tesla.3p.public-key.pem", produces = "application/x-pem-file")
    public ResponseEntity<String> getPublicKeyPem() {
        String pem = keypairService.getPublicKeyAsPem();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header("Content-Disposition", "inline; filename=\"com.tesla.3p.public-key.pem\"")
                // Why: Long-lived public caching is safe for a static public key and reduces load.
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(pem);
    }
}