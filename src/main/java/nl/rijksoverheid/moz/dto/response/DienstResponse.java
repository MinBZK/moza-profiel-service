package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Dienst;

public class DienstResponse {
    public long id;
    public String beschrijving;

    public DienstResponse(Dienst dienst) {
        this.id = dienst.id;
        this.beschrijving = dienst.getBeschrijving();
    }
}
