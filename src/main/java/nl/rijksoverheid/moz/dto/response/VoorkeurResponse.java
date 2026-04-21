package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.VoorkeurType;

import java.time.LocalDateTime;

public class VoorkeurResponse {
    public long id;
    public VoorkeurType voorkeurType;
    public String waarde;
    public LocalDateTime createdAt;
    public LocalDateTime lastUpdated;
    public ScopeResponse scope;
}
