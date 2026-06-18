package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.ContactType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ContactgegevenResponse {
    public UUID id;
    public ContactType type;
    public String waarde;
    public boolean isGeverifieerd;
    public boolean isDefault;
    public Instant createdAt;
    public Instant lastUpdated;
    public Instant teVerwijderenOp;
    public List<ScopeResponse> scopes;
}
