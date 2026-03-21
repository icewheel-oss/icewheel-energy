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

package net.icewheel.energy.application.config;

import java.time.Clock;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import org.modelmapper.ModelMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

	/**
	 * Provides a ModelMapper bean for object-to-object mapping.
	 * @return A new ModelMapper instance.
	 */
	@Bean
	public ModelMapper modelMapper() {
		return new ModelMapper();
	}

	/**
	 * Provides a system-default clock as a bean, which can be replaced by a fixed clock in tests.
	 * @return The default system clock.
	 */
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}

	/**
	 * Provides a singleton bean for the CronParser, configured for Spring's cron format.
	 * This allows for consistent cron expression parsing across the application.
	 * @return A configured CronParser instance.
	 */
	@Bean
	public CronParser cronParser() {
		return new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));
	}
}
