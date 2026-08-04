package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;

public class VoorkeurRequest {

    @NotNull
    public IdentificatieType identificatieType;

    @NotBlank
    public String identificatieNummer;

    @NotNull
    public VoorkeurType voorkeurType;

    @NotNull
    public String waarde;

    public ScopeRequest scope;
}
