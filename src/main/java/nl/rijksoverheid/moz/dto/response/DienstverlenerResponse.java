package nl.rijksoverheid.moz.dto.response;

import nl.rijksoverheid.moz.entity.Dienstverlener;

import java.util.List;

public class DienstverlenerResponse {
    public String naam;
    public String oin;
    public List<DienstResponse> diensten;

    public DienstverlenerResponse(Dienstverlener dv) {
        this.naam = dv.getNaam();
        this.oin = dv.getOin();
        this.diensten = dv.getDiensten()
                .stream()
                .map(DienstResponse::new)
                .toList();
    }
}
