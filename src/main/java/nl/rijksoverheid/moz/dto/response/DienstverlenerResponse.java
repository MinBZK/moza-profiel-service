package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Dienstverlener;

import java.util.List;

public class DienstverlenerResponse {
    public String naam;
    public String oin;
    public List<String> afdelingen;

    public DienstverlenerResponse(Dienstverlener dv) {
        this.naam = dv.getNaam();
        this.oin = dv.getOin();
        this.afdelingen = dv.getAfdelingen()
                .stream()
                .map(Afdeling::getBeschrijving)
                .toList();
    }
}
