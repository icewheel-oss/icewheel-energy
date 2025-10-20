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

package net.icewheel.energy.infrastructure.vendors.tesla.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.domain.Token;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TokenRepository extends JpaRepository<Token, UUID> {

    List<Token> findByUser(User user);
	
	/**
	 * Finds all tokens for a given user, sorted by creation date in descending order.
	 *
	 * @param user The user to find tokens for.
	 * @return A list of tokens, with the most recent one first.
	 */
	List<Token> findByUserOrderByCreatedAtDesc(User user);

	/**
	 * Find a token by the Tesla email address. Use it as backup
	 * method if other ways to find a token fail.
	 * @param email The Tesla account email address.
	 * @return The token, if found.
	 */
    List<Token> findByEmail(String email);

	/**
	 * Finds the most recently created token for a given user.
	 *
	 * @param user The user to find the token for.
	 * @return An Optional containing the most recent token, or empty if none found.
	 */
	Optional<Token> findFirstByUserOrderByCreatedAtDesc(User user);

	/**
	 * Finds the most recently created tokens for a given user and applies a pessimistic write lock.
	 * This prevents other transactions from reading or writing this token until the current transaction completes,
	 * which is crucial for avoiding race conditions during token refresh operations.
	 *
	 * @param user The user to find the token for.
	 * @param pageable A {@link Pageable} object to limit the results (e.g., to the single most recent token).
	 * @return A list of locked tokens.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Token t WHERE t.user = :user ORDER BY t.createdAt DESC")
	List<Token> findByUserWithLockOrderByCreatedAtDesc(User user, Pageable pageable);

}