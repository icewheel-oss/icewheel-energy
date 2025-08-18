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

package net.icewheel.energy.infrastructure.vendors.tesla.auth.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PartnerAccountResponse {
	@JsonProperty("client_id")
	private String clientId;

	@JsonProperty("name")
	private String name;

	@JsonProperty("description")
	private String description;

	@JsonProperty("domain")
	private String domain;

	@JsonProperty("ca")
	private String ca;

	@JsonProperty("created_at")
	private ZonedDateTime createdAt;

	@JsonProperty("updated_at")
	private ZonedDateTime updatedAt;

	@JsonProperty("enterprise_tier")
	private String enterpriseTier;

	@JsonProperty("account_id")
	private String accountId;

	@JsonProperty("issuer")
	private String issuer;

	@JsonProperty("csr")
	private String csr;

	@JsonProperty("csr_updated_at")
	private ZonedDateTime csrUpdatedAt;

	@JsonProperty("public_key")
	private String publicKey;

	@JsonProperty("public_key_hash")
	private String publicKeyHash;
}