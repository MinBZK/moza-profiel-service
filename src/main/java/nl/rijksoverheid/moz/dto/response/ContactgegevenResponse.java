package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.ContactType;

public abstract class ContactgegevenResponse {

    public ContactType type;
    public String waarde;
    public boolean isGeverifieerd;
}
