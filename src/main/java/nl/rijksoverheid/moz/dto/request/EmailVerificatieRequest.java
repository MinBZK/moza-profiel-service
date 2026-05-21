package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.validation.ValidIdentificatieNummer;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor het verifiëren van een emailadres")
@ValidIdentificatieNummer
public class EmailVerificatieRequest {

    @NotNull
    public String email;

    @NotNull
    public String identificatieNummer;

    @NotNull
    public IdentificatieType identificatieType;

    @NotNull
    public String verificatieCode;
}
