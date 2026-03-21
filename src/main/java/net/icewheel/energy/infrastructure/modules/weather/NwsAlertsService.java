package net.icewheel.energy.infrastructure.modules.weather;

import java.util.List;
import java.util.Optional;

import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.AlertCollectionJsonLd;

/**
 * Service for interacting with the NWS API.
 */
public interface NwsAlertsService {

    AlertCollectionJsonLd getAlerts(Boolean active, String start, String end, String status, String messageType, String event, String code, String area, String point, String region, String regionType, String zone, String urgency, String severity, String certainty, Integer limit, String cursor);

    Object getGlossary();

    Optional<String> getStations(User user, List<String> id, List<String> state, Integer limit, String cursor);

    Object getProducts();
    

    

}
