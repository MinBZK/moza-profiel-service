package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;

public class PartijIdentificatieRequest {

    @NotNull
    public IdentificatieType identificatieType;

    @NotBlank
    public String identificatieNummer;
}
