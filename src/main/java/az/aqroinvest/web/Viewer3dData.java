package az.aqroinvest.web;

import az.aqroinvest.domain.Plot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data for Leaflet popups and the 3D viewer (map.js open3d → Plot3D.open). Colour comes from ACTIVITY_COLORS in map.js.
 * applyUrl only for an open competition and non-staff; anonymous users are sent to login and back to the form.
 */
@Component
@RequiredArgsConstructor
public class Viewer3dData {

    private final StatusLabels labels;
    private final Fmt fmt;

    public Map<String, Object> of(Plot p, boolean openCompetition, Authentication auth) {
        boolean staff = auth != null && auth.getAuthorities().stream().anyMatch(a -> !a.getAuthority().equals("ROLE_INVESTOR"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("subtitle", p.getDistrict() + " · " + fmt.dec(p.getAreaHa()) + " ha · " + labels.label(p.getPurpose()));
        m.put("purpose", p.getPurpose().name());
        m.put("geoJson", p.getGeoJson());
        m.put("applyUrl", openCompetition && !staff ? "/kabinet/muraciet/yeni?plotId=" + p.getId() : null);
        return m;
    }
}
