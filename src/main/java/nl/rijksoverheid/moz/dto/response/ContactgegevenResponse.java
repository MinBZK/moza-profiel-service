package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.Taal;

public class ContactgegevenResponse {
    public Long id;
    public ContactType type;
    public String waarde;
    public Taal taal;
    public String terAttentieVan;
    public boolean isGeverifieerd;
    public ScopeResponse scope;
}
