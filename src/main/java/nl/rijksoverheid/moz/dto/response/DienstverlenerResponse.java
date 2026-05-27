package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;

import java.util.List;

public class DienstverlenerResponse {
    public String naam;
    public String beschrijving;
    public List<DienstResponse> diensten;

    public DienstverlenerResponse() {
    }

    public DienstverlenerResponse(Dienstverlener dv, List<Dienst> diensten) {
        this.naam = dv.getNaam();
        this.beschrijving = dv.getBeschrijving();
        this.diensten = diensten.stream().map(DienstResponse::new).toList();
    }
}
