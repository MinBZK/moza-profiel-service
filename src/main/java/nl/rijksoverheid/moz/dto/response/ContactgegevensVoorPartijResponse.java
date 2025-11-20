package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.ContactType;

public class ContactgegevensVoorPartijResponse {
    public Long id;
    public ContactType type;
    public String waarde;
    public boolean isGeverifieerd;
    public AfdelingResponse afdeling;
}