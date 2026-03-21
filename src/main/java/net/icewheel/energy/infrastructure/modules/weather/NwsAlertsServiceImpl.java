package net.icewheel.energy.infrastructure.modules.weather;

import java.util.List;
import java.util.Optional;

import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.infrastructure.modules.location.zip.service.ZipCodeService;
import net.icewheel.energy.infrastructure.modules.weather.deserializer.NwsWebClient;
import net.icewheel.energy.infrastructure.weather.nws.gen.model.AlertCollectionJsonLd;

import org.springframework.stereotype.Service;

/**
 * Implementation of the {@link NwsAlertsService} interface.
 */
@Service
public class NwsAlertsServiceImpl implements NwsAlertsService {

    private final NwsWebClient nwsWebClient;
    private final ZipCodeService zipCodeService;

    public NwsAlertsServiceImpl(NwsWebClient nwsWebClient, ZipCodeService zipCodeService) {
        this.nwsWebClient = nwsWebClient;
        this.zipCodeService = zipCodeService;
    }

    @Override
    public AlertCollectionJsonLd getAlerts(Boolean active, String start, String end, String status, String messageType, String event, String code, String area, String point, String region, String regionType, String zone, String urgency, String severity, String certainty, Integer limit, String cursor) {
        return nwsWebClient.getAlerts(active, start, end, status, messageType, event, code, area, point, region, regionType, zone, urgency, severity, certainty, limit, cursor);
    }

    @Override
    public Object getGlossary() {
        return nwsWebClient.getGlossary();
    }

    @Override
    public Optional<String> getStations(User user, List<String> id, List<String> state, Integer limit, String cursor) {
        if (user != null && user.getPreference() != null && user.getPreference().getZipCode() != null) {
            return zipCodeService.getCoordinates(user.getPreference().getZipCode())
                    .map(places -> {
                        if (places.isEmpty()) {
                            return null;
                        }
                        return nwsWebClient.getGridpointStations(places.getFirst().getLatitude(), places.getFirst().getLongitude(), limit);
                    });
        }
        return Optional.ofNullable(nwsWebClient.getStations(id, state, limit, cursor));
    }

    @Override
    public Object getProducts() {
        return nwsWebClient.getProducts();
    }
}
