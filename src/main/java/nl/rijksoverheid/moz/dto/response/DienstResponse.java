package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Dienst;

import java.util.UUID;

public class DienstResponse {
    public UUID id;
    public String naam;
    public String beschrijving;

    public DienstResponse() {
    }

    public DienstResponse(Dienst dienst) {
        this.id = dienst.id;
        this.naam = dienst.getNaam();
        this.beschrijving = dienst.getBeschrijving();
    }
}
