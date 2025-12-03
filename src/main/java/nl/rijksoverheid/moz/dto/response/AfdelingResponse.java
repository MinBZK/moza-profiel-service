package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Dienstverlener;

public class AfdelingResponse {
    public long id;
    public String beschrijving;

    public AfdelingResponse(Afdeling afdeling) {
        this.id = afdeling.id;
        this.beschrijving = afdeling.getBeschrijving();
    }

}