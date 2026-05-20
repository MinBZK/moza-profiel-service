package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor het toevoegen van een dienstverlener")
public class DienstverlenerRequest {

    @NotNull
    public String naam;

    public String beschrijving;
}
