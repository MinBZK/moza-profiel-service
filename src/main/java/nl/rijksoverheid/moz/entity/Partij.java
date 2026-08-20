package nl.rijksoverheid.moz.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Partij extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Nullable
    private Instant verwijderdOp;

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Identificatie> identificaties = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contactgegeven> contactgegevens = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Voorkeur> voorkeuren = new ArrayList<>();

    @Nullable
    public Instant getVerwijderdOp() {
        return verwijderdOp;
    }

    public void setVerwijderdOp(Instant verwijderdOp) {
        this.verwijderdOp = verwijderdOp;
    }

    public boolean isVerwijderd() {
        return verwijderdOp != null;
    }

    /** Idempotent: een al gezette verwijderdOp blijft staan — geen resurrection, geen overschrijven. */
    public void verwijder(Instant nu) {
        if (verwijderdOp == null) {
            verwijderdOp = nu;
        }
    }

    public List<Voorkeur> getVoorkeuren() {
        return Collections.unmodifiableList(voorkeuren);
    }

    public static Partij findByIdentificatie(IdentificatieType type, String nummer) {
        return find("""
        SELECT p FROM Partij p
        JOIN p.identificaties i
        WHERE i.identificatieType = ?1
          AND i.identificatieNummer = ?2
          AND p.verwijderdOp IS NULL
    """, type, nummer).firstResult();
    }

    public void addIdentificatie(Identificatie identificatie) {
        identificaties.add(identificatie);
        identificatie.setPartij(this);
    }

    public void addVoorkeur(Voorkeur voorkeur) {
        voorkeuren.add(voorkeur);
        voorkeur.setPartij(this);
    }

    public List<Identificatie> getIdentificaties() {
        return Collections.unmodifiableList(identificaties);
    }

    /**
     * Deterministische keuze uit de actieve identificaties, voor wanneer precies één identificatie
     * nodig is — momenteel alleen als AVG-dataSubject in de retentiescheduler's logboekvermelding
     * (RetentieScheduler.registreerLogboekNaCommit). Filtert soft deletes weg: zonder die filter
     * zou een verwijderde identificatie als dataSubject gelogd kunnen worden. Prioriteit BSN > KVK
     * > RSIN (IdentificatieType's declaratievolgorde): bij een partij met zowel BSN als KVK is BSN
     * de natuurlijke persoon achter de KVK-inschrijving, en die is de bewuste keuze als
     * dataSubject. Nummer als tweede sleutel is puur voor determinisme; twee actieve rijen van
     * hetzelfde type zijn in theorie onmogelijk dankzij uk_identificatie_per_partij. {@code null}
     * als de partij geen actieve identificaties heeft (invariant violation, zie findOrCreatePartij
     * — of alle identificaties zijn mee-gecascadet met de partij zelf).
     */
    public Identificatie primaireIdentificatie() {
        return identificaties.stream()
                .filter(i -> i.getVerwijderdOp() == null)
                .min(Comparator.comparing((Identificatie i) -> i.getIdentificatieType().ordinal())
                        .thenComparing(Identificatie::getIdentificatieNummer))
                .orElse(null);
    }

    public List<Contactgegeven> getContactgegevens() {
        return Collections.unmodifiableList(contactgegevens);
    }
}
