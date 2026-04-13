package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.Taal;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor het toevoegen van een contactgegeven aan een partij")
public class ContactgegevenRequest {

    public long afdelingId;

    @NotNull
    public ContactType type;

    @NotNull
    public String waarde;

    public Taal taal;

    public String terAttentieVan;

    public IdentificatieType scopeIdentificatieType;

    public String scopeIdentificatieNummer;
}
