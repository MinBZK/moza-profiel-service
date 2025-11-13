package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.ContactvoorkeurType;
import nl.rijksoverheid.moz.common.Scope;

public abstract class ContactvoorkeurResponse {

    public Long id;
    public Scope scope;
    public String dienstType;
    public ContactvoorkeurType type;
    public String waarde;
    public boolean isGeverifieerd;
}
