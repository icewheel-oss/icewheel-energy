
package net.icewheel.energy.api.rest.controller;

import lombok.RequiredArgsConstructor;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.modules.weather.NwsAlertsService;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TeslaUserService;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.AlertCollectionJsonLd;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A controller for handling NWS API requests.
 */
@RestController
@RequestMapping("/api/nws")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NwsApiController {

    private final NwsAlertsService nwsAlertsService;
    private final TeslaUserService teslaUserService;

    /**
     * Retrieves active weather alerts.
     *
     * @return A {@link ResponseEntity} containing a {@link AlertCollectionJsonLd} with the active alerts.
     */
    @GetMapping(value = "/alerts")
    public ResponseEntity<AlertCollectionJsonLd> getAlerts(@RequestParam(required = false) String point) {
        return ResponseEntity.ok(nwsAlertsService.getAlerts(true, null, null, null, null, null, null, null, point, null, null, null, null, null, null, null, null));
    }

    /**
     * Retrieves the NWS glossary.
     *
     * @return A {@link ResponseEntity} containing a {@link Object} with the glossary.
     */
    @GetMapping(value = "/glossary")
    public ResponseEntity<Object> getGlossary() {
        return ResponseEntity.ok(nwsAlertsService.getGlossary());
    }

    /**
     * Retrieves weather stations for the authenticated user.
     *
     * @param oauth2User The authenticated user.
     * @return A {@link ResponseEntity} containing a {@link Object} with the weather stations.
     */
    @GetMapping(value = "/stations")
    public ResponseEntity<String> getStations(@AuthenticationPrincipal OAuth2User oauth2User) {
        User user = teslaUserService.findOrCreateUser(oauth2User);
        return nwsAlertsService.getStations(user, null, null, null, null)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves weather products.
     *
     * @return A {@link ResponseEntity} containing a {@link Object} with the weather products.
     */
    @GetMapping(value = "/products")
    public ResponseEntity<Object> getProducts() {
        return ResponseEntity.ok(nwsAlertsService.getProducts());
    }
}
