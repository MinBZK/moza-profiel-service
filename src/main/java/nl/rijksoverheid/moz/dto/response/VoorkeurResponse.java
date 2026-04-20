package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.VoorkeurType;

public class VoorkeurResponse {
    public long id;
    public VoorkeurType voorkeurType;
    public String waarde;
    public ScopeResponse scope;
}
