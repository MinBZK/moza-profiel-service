package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.VoorkeurType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class VoorkeurResponse {
    public UUID id;
    public VoorkeurType voorkeurType;
    public String waarde;
    public Instant createdAt;
    public Instant lastUpdated;
    public Instant teVerwijderenOp;
    public List<ScopeResponse> scopes;
}
