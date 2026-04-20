package nl.rijksoverheid.moz.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;

@ApplicationScoped
public class DienstverlenerService {

    @Transactional
    public void addDienstverlener(DienstverlenerRequest dienstverlenerRequest) {
        findOrCreateDienstverlener(dienstverlenerRequest.naam, dienstverlenerRequest.oin);
    }

    @Transactional
    public Dienstverlener getDienstenVoorDienstverlener(String naam) {
        return Dienstverlener.find("naam = ?1", naam).firstResult();
    }

    @Transactional
    public Dienst addDienstToDienstverlener(String naam, DienstRequest request) {
        var dienstverlener = findOrCreateDienstverlener(naam, null);

        Dienst dienst = new Dienst();
        dienst.setBeschrijving(request.beschrijving);
        dienst.setDienstverlener(dienstverlener);

        dienstverlener.addDienst(dienst);

        dienst.persist();
        dienstverlener.persist();

        return dienst;
    }

    @Transactional
    public Dienstverlener findOrCreateDienstverlener(String naam, String oin) {
        Dienstverlener dienstverlener = Dienstverlener.find(
                "lower(naam) = lower(?1)",
                naam
        ).firstResult();

        if (dienstverlener != null) {
            return dienstverlener;
        }

        dienstverlener = new Dienstverlener();
        dienstverlener.setNaam(naam);
        dienstverlener.setOin(oin);

        Dienst defaultDienst = new Dienst();
        defaultDienst.setBeschrijving("Alles");
        defaultDienst.setDienstverlener(dienstverlener);
        dienstverlener.addDienst(defaultDienst);

        dienstverlener.persist();

        return dienstverlener;
    }

}
