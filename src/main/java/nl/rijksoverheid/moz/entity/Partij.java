package nl.rijksoverheid.moz.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Partij extends VerwijderbareEntiteit {

    @Id
    @GeneratedValue
    public UUID id;

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Identificatie> identificaties = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contactgegeven> contactgegevens = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Voorkeur> voorkeuren = new ArrayList<>();

    public List<Voorkeur> getVoorkeuren() {
        return Collections.unmodifiableList(voorkeuren);
    }

    @Override
    Object entiteitId() {
        return id;
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
     * Deterministische keuze uit de actieve identificaties: filtert soft deletes weg en volgt
     * IdentificatieType's declaratievolgorde (BSN > KVK > RSIN) als prioriteit.
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
