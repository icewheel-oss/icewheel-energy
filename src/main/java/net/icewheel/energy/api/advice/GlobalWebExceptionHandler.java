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

package net.icewheel.energy.api.advice;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(annotations = Controller.class)
public class GlobalWebExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public String handleResponseStatusException(ResponseStatusException ex, RedirectAttributes redirectAttributes) {
		if (ex.getStatusCode() == HttpStatus.FORBIDDEN && "Tesla account not connected".equals(ex.getReason())) {
			redirectAttributes.addFlashAttribute("globalError", "You must connect your Tesla account to access that page. Please connect your account from the settings page.");
			return "redirect:/";
		}
		throw ex;
	}
}