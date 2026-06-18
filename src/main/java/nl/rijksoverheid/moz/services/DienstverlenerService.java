package nl.rijksoverheid.moz.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.BusinessException.Kind;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class DienstverlenerService {

    private static final Logger LOG = Logger.getLogger(DienstverlenerService.class);

    @Transactional
    public Dienstverlener addDienstverlener(DienstverlenerRequest request) {
        return findOrCreateDienstverlener(request.naam, request.beschrijving);
    }

    @Transactional
    public Dienstverlener getDienstverlener(String naam) {
        return Dienstverlener.find("lower(naam) = lower(?1)", naam).firstResult();
    }

    @Transactional
    public Dienst addDienstToDienstverlener(String dienstverlenerNaam, DienstRequest request) {
        Dienstverlener dienstverlener = findOrCreateDienstverlener(dienstverlenerNaam, null);

        Dienst dienst = Dienst.findByNaam(request.naam);
        if (dienst == null) {
            dienst = new Dienst();
            dienst.setNaam(request.naam);
            dienst.setBeschrijving(request.beschrijving);
            dienst.persist();
        } else if (request.beschrijving != null
                && !Objects.equals(dienst.getBeschrijving(), request.beschrijving)) {
            throw new BusinessException(Kind.CONFLICT,
                    "Dienst bestaat al met een andere beschrijving. Laat 'beschrijving' weg of stuur dezelfde waarde.");
        }

        findOrCreateDienstverlenerDienst(dienstverlener, dienst);
        return dienst;
    }

    @Transactional
    public List<Dienst> getDienstenVoorDienstverlener(Dienstverlener dienstverlener) {
        return DienstverlenerDienst
                .<DienstverlenerDienst>find("dienstverlener = ?1 AND dienst IS NOT NULL", dienstverlener)
                .stream()
                .map(DienstverlenerDienst::getDienst)
                .toList();
    }

    @Transactional
    public Dienstverlener findOrCreateDienstverlener(String naam, String beschrijving) {
        Dienstverlener dienstverlener = Dienstverlener.find("lower(naam) = lower(?1)", naam).firstResult();
        if (dienstverlener != null) {
            return dienstverlener;
        }

        LOG.info("Nieuwe dienstverlener aanmaken");
        dienstverlener = new Dienstverlener();
        dienstverlener.setNaam(naam);
        dienstverlener.setBeschrijving(beschrijving);
        dienstverlener.persist();
        return dienstverlener;
    }

    @Transactional
    public DienstverlenerDienst findOrCreateDienstverlenerDienst(Dienstverlener dienstverlener, Dienst dienst) {
        // SQL kan niet "kolom = NULL" matchen (altijd unknown), dus dienst==null vereist een
        // aparte query. Zonder deze split werd elke DV-brede scope-call (dienstNaam ontbreekt)
        // een duplicate rij omdat de lookup nooit hit gaf en de UNIQUE(dv_id, dienst_id)
        // constraint geen NULLs dedupliceert.
        DienstverlenerDienst link;
        if (dienst == null) {
            link = DienstverlenerDienst.find(
                    "dienstverlener = ?1 AND dienst IS NULL",
                    dienstverlener
            ).firstResult();
        } else {
            link = DienstverlenerDienst.find(
                    "dienstverlener = ?1 AND dienst = ?2",
                    dienstverlener, dienst
            ).firstResult();
        }

        if (link != null) {
            return link;
        }

        link = new DienstverlenerDienst(dienstverlener, dienst);
        link.persist();
        return link;
    }
}
