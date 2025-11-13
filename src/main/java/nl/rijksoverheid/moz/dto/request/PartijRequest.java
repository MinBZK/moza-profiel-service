package nl.rijksoverheid.moz.dto.request;

import nl.rijksoverheid.moz.common.ContactvoorkeurType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.Scope;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request object voor het toevoegen van een contactvoorkeur aan een partij")
public class PartijRequest {
    @Schema(description = "Type identificatie (BSN, KVK, RSIN)", required = true, examples = "BSN")
    public IdentificatieType identificatieType;

    @Schema(description = "Het identificatienummer", required = true, examples = "123456789")
    public String identificatieNummer;

    @Schema(description = "De waarde van de contactvoorkeur (bijv. emailadres of telefoonnummer)", required = true, examples = "test@example.com")
    public String waarde;

    @Schema(description = "Type contactvoorkeur", required = true, examples = "Email")
    public ContactvoorkeurType type;

    @Schema(description = "Scope", required = true, examples = "Zakelijk")
    public Scope scope;

    @Schema(description = "Dienst type", required = true, examples = "00000000000000000000")
    public String dienstType;
}