package nl.rijksoverheid.moz.dto.response;

import java.util.List;
import java.util.UUID;

public class PartijResponse {
    public UUID partijId;
    public List<IdentificatieResponse> identificaties;
    public List<VoorkeurResponse> voorkeuren;
    public List<ContactgegevenResponse> contactgegevens;
}
