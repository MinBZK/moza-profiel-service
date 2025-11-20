package nl.rijksoverheid.moz.dto.request;

import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.entity.Afdeling;

public class ContactgegevenUpdateRequest {
    public long id;
    public ContactType type;
    public String waarde;
    public String afdeling;
}
