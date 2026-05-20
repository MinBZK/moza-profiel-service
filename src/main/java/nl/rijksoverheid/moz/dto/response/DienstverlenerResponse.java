package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Dienstverlener;

public class DienstverlenerResponse {
    public String naam;
    public String beschrijving;

    public DienstverlenerResponse() {
    }

    public DienstverlenerResponse(Dienstverlener dv) {
        this.naam = dv.getNaam();
        this.beschrijving = dv.getBeschrijving();
    }
}
