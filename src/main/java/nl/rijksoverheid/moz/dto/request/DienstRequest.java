package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor het toevoegen van een dienst aan een dienstverlener")
public class DienstRequest {
    @NotBlank
    public String naam;

    public String beschrijving;
}
