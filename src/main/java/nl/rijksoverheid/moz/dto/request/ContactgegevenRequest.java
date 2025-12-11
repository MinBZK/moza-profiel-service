package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.ContactType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor het toevoegen van een contactgegeven aan een partij")
public class ContactgegevenRequest {

    public long afdelingId;

    @NotNull
    public ContactType type;       // enum ContactType (EMAIL, TELEFOON, etc.)

    @NotNull
    public String waarde;          // het adres, nummer of waarde zelf
}
