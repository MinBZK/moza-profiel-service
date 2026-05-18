package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.VoorkeurType;

import java.time.LocalDateTime;
import java.util.List;

public class VoorkeurResponse {
    public long id;
    public VoorkeurType voorkeurType;
    public String waarde;
    public LocalDateTime createdAt;
    public LocalDateTime lastUpdated;
    public List<ScopeResponse> scopes;
}
