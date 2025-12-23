package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.VoorkeurType;

public class VoorkeurRequest {


    @NotNull
    public VoorkeurType voorkeurType;

    @NotNull
    public String waarde;

}
