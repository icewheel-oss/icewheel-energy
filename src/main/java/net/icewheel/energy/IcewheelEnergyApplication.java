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

package net.icewheel.energy;


import net.icewheel.energy.application.scheduling.MisfireHandlingService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the Icewheel Energy application.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableAspectJAutoProxy
public class IcewheelEnergyApplication {

	/**
	 * The main method, which uses Spring Boot to run the application.
	 * @param args Command line arguments.
	 */
	public static void main(String[] args) {
		SpringApplication.run(IcewheelEnergyApplication.class, args);
	}

	/**
	 * A Spring Bean that runs on application startup to handle any misfired schedules.
	 * @param misfireHandlingService The service that handles misfired schedules.
	 * @return A CommandLineRunner that executes the misfire handling logic.
	 */
	@Bean
	public CommandLineRunner runMisfireHandler(MisfireHandlingService misfireHandlingService) {
		return args -> {
			misfireHandlingService.handleMisfiredSchedules();
		};
	}

}