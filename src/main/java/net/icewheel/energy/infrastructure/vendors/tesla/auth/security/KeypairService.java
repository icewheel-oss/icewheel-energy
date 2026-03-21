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

package net.icewheel.energy.infrastructure.vendors.tesla.auth.security;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.Keypair;
import net.icewheel.energy.infrastructure.vendors.tesla.repository.KeypairRepository;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

/**
 * Manages the application's cryptographic keypair required for interacting with the Tesla Fleet API.
 * <p>
 * This service is responsible for ensuring that a valid EC keypair (using the secp256r1 curve)
 * exists for the application. On startup, it attempts to load the keypair from the database.
 * If no keypair is found, it generates a new one, persists it, and then uses it for the
 * application's lifecycle.
 * <p>
 * The public key is exposed via a well-known endpoint for domain verification by Tesla,
 * and the private key is used for signing sensitive API requests.
 */
@Service
@Slf4j
public class KeypairService implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * The repository for persisting and retrieving the keypair from the database.
     */
    private final KeypairRepository keypairRepository;
    /**
     * The active cryptographic keypair used by the application. This is initialized on startup.
     */
    private KeyPair keyPair;

    /**
     * Constructs the KeypairService.
     *
     * @param keypairRepository The repository for keypair persistence.
     */
    public KeypairService(KeypairRepository keypairRepository) {
        this.keypairRepository = keypairRepository;
        // Ensure the Bouncy Castle security provider is available for cryptographic operations.
        // This provides a consistent and reliable cryptographic implementation.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Initializes the keypair when the application is ready.
     * This method is triggered by Spring Boot after the application context is fully loaded.
     * It ensures that the keypair is available before any services that might need it are used.
     *
     * @param event The application ready event.
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Application is ready. Initializing cryptographic keypair...");
        try {
            initializeKeyPair();
            log.info("Keypair initialized successfully.");
        } catch (Exception e) {
            log.error("FATAL: Could not initialize cryptographic keypair. Application will not function correctly.", e);
            // Re-throwing as a runtime exception will cause the application startup to fail, which is appropriate.
            throw new RuntimeException("Failed to initialize KeypairService", e);
        }
    }

    /**
     * Loads the keypair from the database or generates a new one if none exists.
     * <p>
     * The service standardizes on storing keys in their raw DER-encoded format (X.509 for public,
     * PKCS#8 for private) to ensure consistency and avoid complex parsing logic. If a keypair
     * with the ID "default" is not found, a new one is generated using the 'secp256r1' curve
     * (also known as 'prime256v1'), which is standard for the Tesla Fleet API.
     *
     * @throws NoSuchAlgorithmException if the required EC algorithm is not available.
     * @throws InvalidAlgorithmParameterException if the EC generation parameters are invalid.
     * @throws InvalidKeySpecException if the key data from the database is invalid.
     */
    private void initializeKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeySpecException, NoSuchProviderException {
        // Standardize on a single key ID for the application's keypair.
        final String keypairId = "default";
        Optional<Keypair> optionalKeypair = keypairRepository.findById(keypairId);

        if (optionalKeypair.isPresent()) {
            log.info("Loading existing keypair from database.");
            Keypair keypair = optionalKeypair.get();
            // The service will now exclusively use the DER format (byte array) for storage and retrieval.
            // This removes the complex and brittle logic for parsing multiple formats.
            KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(keypair.getPublicKey()));
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keypair.getPrivateKey()));
            this.keyPair = new KeyPair(publicKey, privateKey);
        } else {
            log.info("No existing keypair found. Generating and saving a new one.");
            // Use secp256r1 (also known as prime256v1), which is standard for this type of application.
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            this.keyPair = keyPairGenerator.generateKeyPair();

            // Create and save the new keypair in the standardized DER format.
            Keypair newKeypair = new Keypair();
            newKeypair.setId(keypairId);
            newKeypair.setPublicKey(this.keyPair.getPublic().getEncoded());
            newKeypair.setPrivateKey(this.keyPair.getPrivate().getEncoded());
            keypairRepository.save(newKeypair);
            log.info("New keypair generated and saved to database with ID '{}'.", keypairId);
        }
    }

    /**
     * Gets the public key of the application's keypair.
     *
     * @return The {@link PublicKey}.
     */
    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    /**
     * Gets the private key of the application's keypair.
     *
     * @return The {@link PrivateKey}.
     */
    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    /**
     * Returns the public key formatted as a PEM string.
     * This format is required by Tesla for the well-known endpoint used for domain verification.
     *
     * @return A string containing the public key in PEM format.
     */
    public String getPublicKeyAsPem() {
        String header = "-----BEGIN PUBLIC KEY-----";
        String footer = "-----END PUBLIC KEY-----";
        String base64 = Base64.getEncoder().encodeToString(getPublicKey().getEncoded());
        return header + "\n" + base64 + "\n" + footer;
    }
}
