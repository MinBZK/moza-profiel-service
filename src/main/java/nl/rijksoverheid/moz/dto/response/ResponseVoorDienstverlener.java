package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.common.IdentificatieType;

import java.util.List;

public class ResponseVoorDienstverlener {
    public Long partijId;
    public IdentificatieType identificatieType; // De meegestuurde identificatie
    public String identificatieNummer;
    public List<ContactgegevenResponse> contactgegevens;
}

