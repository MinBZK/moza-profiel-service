package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.ContactType;

import java.time.LocalDateTime;

public class ContactgegevenResponse {
    public Long id;
    public ContactType type;
    public String waarde;
    public boolean isGeverifieerd;
    public boolean nogSteedsValide;
    public LocalDateTime createdAt;
    public LocalDateTime lastUpdated;
    public ScopeResponse scope;
}
