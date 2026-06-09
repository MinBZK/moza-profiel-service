package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;

import java.util.UUID;

public class ClearTeVerwijderenOpRequest {

    @NotNull
    public UUID id;

    @NotNull
    public IdentificatieType identificatieType;

    @NotBlank
    public String identificatieNummer;

    @NotBlank
    public String dienstverlenerNaam;

    public String dienstNaam;
}
