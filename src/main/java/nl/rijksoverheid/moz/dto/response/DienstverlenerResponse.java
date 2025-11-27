package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Dienstverlener;

import java.util.HashMap;
import java.util.List;

public class DienstverlenerResponse {
    public String naam;
    public String oin;
    public List<HashMap<Long, String>> afdelingen;

    public DienstverlenerResponse(Dienstverlener dv) {
        this.naam = dv.getNaam();
        this.oin = dv.getOin();
        this.afdelingen = dv.getAfdelingen()
                .stream()
                .map(afdeling -> {
                    HashMap<Long, String> map = new HashMap<>();
                    map.put(afdeling.id, afdeling.getBeschrijving());
                    return map;
                })
                .toList();
    }
}
