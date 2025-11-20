package nl.rijksoverheid.moz.dto.response;

import java.util.List;

public class ResponseVoorPartij {
    public Long partijId;
    public List<IdentificatieResponse> identificaties;
    public List<VoorkeurResponse> voorkeuren;
    public List<ContactgegevensVoorPartijResponse> contactgegevens;
}


