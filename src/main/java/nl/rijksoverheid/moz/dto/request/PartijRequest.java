package nl.rijksoverheid.moz.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor het ophalen van een Partij")
public class PartijRequest {

    @NotNull
    public IdentificatieType identificatieType;

    @NotBlank
    public String identificatieNummer;

    public String dienstverlener;


    public String dienstNaam;

    @JsonIgnore
    public boolean isEmpty() {
        return dienstverlener == null && dienstNaam == null;
    }
}
