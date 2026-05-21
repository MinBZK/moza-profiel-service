package nl.rijksoverheid.moz.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor extra informatie meesturen bij van een Partij")
public class PartijRequest {

    @QueryParam("dienstverlener")
    public String dienstverlener;

    @QueryParam("dienstNaam")
    public String dienstNaam;

    @JsonIgnore
    public boolean isEmpty() {
        return dienstverlener == null && dienstNaam == null;
    }
}
