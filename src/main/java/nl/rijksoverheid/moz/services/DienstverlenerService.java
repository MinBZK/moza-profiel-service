package nl.rijksoverheid.moz.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.dto.request.AfdelingRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Dienstverlener;

@ApplicationScoped
public class DienstverlenerService {

    @Transactional
    public void addDienstverlener(DienstverlenerRequest dienstverlenerRequest) {

        findOrCreateDienstverlener(dienstverlenerRequest.naam, dienstverlenerRequest.oin);
    }

    @Transactional
    public Dienstverlener getAfdelingenVoorDienstverlener(String naam) {
        return Dienstverlener.find("naam = ?1", naam).firstResult();
    }

    @Transactional
    public Afdeling addAfdelingToDienstverlener(String naam, AfdelingRequest request) {

        var dienstverlener = findOrCreateDienstverlener(naam, null);

        Afdeling afdeling = new Afdeling();
        afdeling.setBeschrijving(request.beschrijving);
        afdeling.setDienstverlener(dienstverlener);

        dienstverlener.getAfdelingen().add(afdeling);

        afdeling.persist();
        dienstverlener.persist();

        return afdeling;
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

        // Voeg standaard afdeling 'Alles' toe bij het aanmaken van een nieuwe dienstverlener
        Afdeling defaultAfdeling = new Afdeling();
        defaultAfdeling.setBeschrijving("Alles");
        defaultAfdeling.setDienstverlener(dienstverlener);
        dienstverlener.getAfdelingen().add(defaultAfdeling);

        // Persist de dienstverlener (cascadet ook de aangemaakte afdeling)
        dienstverlener.persist();

        return dienstverlener;
    }

}

