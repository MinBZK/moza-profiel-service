package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;

public class EmailVerificatieCodeAanvraagRequest {

    @NotBlank
    public String email;

    @NotBlank
    public String identificatieNummer;

    @NotNull
    public IdentificatieType identificatieType;
}
