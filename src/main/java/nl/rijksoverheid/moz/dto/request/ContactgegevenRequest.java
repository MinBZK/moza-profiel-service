package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Request object voor het toevoegen van een contactgegeven aan een partij")
public class ContactgegevenRequest {

    @NotNull
    public IdentificatieType identificatieType;

    @NotBlank
    public String identificatieNummer;

    @NotNull
    public ContactType type;

    @NotNull
    public String waarde;

    public ScopeRequest scope;

    public Instant teVerwijderenOp;
}
