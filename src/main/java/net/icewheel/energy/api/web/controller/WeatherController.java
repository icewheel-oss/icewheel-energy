package net.icewheel.energy.api.web.controller;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.modules.weather.WeatherService;
import net.icewheel.energy.infrastructure.modules.weather.dto.WeatherPageData;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaUserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final TeslaUserService teslaUserService;
    private final WeatherService weatherService;

    /**
     * Displays the weather page for a given zip code or the user's default zip code.
     *
     * This method determines the weather forecast to display based on the following priority:
     * 1. A `zipCode` request parameter, if provided.
     * 2. The user's saved zip code from their preferences.
     *
     * The timezone for the weather data is determined by the NWS API based on the location of the zip code.
     * If a user travels to a different timezone, the weather page will continue to display the weather
     * for the zip code stored in their preferences, not their current location. To view the weather for their
     * current location, they would need to manually enter the zip code.
     *
     * @param model The Spring MVC model.
     * @param oauth2User The authenticated user.
     * @param zipCode The zip code to get the weather for (optional).
     * @return The name of the weather view.
     */
    @GetMapping(value = {"", "/"})
    public String weather(Model model, @AuthenticationPrincipal OAuth2User oauth2User, @RequestParam(required = false) String zipCode) {
        model.addAttribute("activePage", "weather");
        model.addAttribute("pageTitle", "Weather");

        if (oauth2User == null) {
            return "redirect:/login.html";
        }
        User user = teslaUserService.findOrCreateUser(oauth2User);

        Optional<WeatherPageData> weatherDataOptional;
        if (zipCode != null && !zipCode.isBlank()) {
            weatherDataOptional = weatherService.getWeatherPageDataForZipCode(zipCode);
        } else {
            weatherDataOptional = weatherService.getWeatherPageDataForUser(user);
        }

        if (weatherDataOptional.isPresent()) {
            WeatherPageData data = weatherDataOptional.get();
            model.addAttribute("rawHourlyForecastJson", data.getRawHourlyForecastJson());
            model.addAttribute("rawDailyForecastJson", data.getRawDailyForecastJson());
            model.addAttribute("locationName", data.getLocationName());
			model.addAttribute("rawPointsJson", data.getRawPointsJson());
            model.addAttribute("rawObservationStationsJson", data.getRawObservationStationsJson());
            model.addAttribute("rawObservationJson", data.getRawObservationJson());
            model.addAttribute("locationTimeZone", data.getLocationTimeZone());
        } else {
            model.addAttribute("errorMessage", "Could not retrieve weather data. Please set a valid zip code in your <a href=\"/user-profile\">profile</a> or enter one.");
        }
        return "weather";
    }

    @GetMapping("/weather-tools")
    public String weatherTools() {
        return "weather-tools";
    }

    @GetMapping("/refresh")
    public String refreshWeather(@AuthenticationPrincipal OAuth2User oauth2User, RedirectAttributes redirectAttributes) {
        if (oauth2User == null) {
            return "redirect:/login.html";
        }
        User user = teslaUserService.findOrCreateUser(oauth2User);

        weatherService.forceRefreshForUser(user);
        redirectAttributes.addFlashAttribute("successMessage", "Weather cache has been refreshed.");
        return "redirect:/weather";
    }
}
