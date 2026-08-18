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

    public List<Voorkeur> getVoorkeuren() {
        return Collections.unmodifiableList(voorkeuren);
    }

    public void setVoorkeuren(List<Voorkeur> voorkeuren) {
        this.voorkeuren.clear();
        this.voorkeuren.addAll(voorkeuren);
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

    public void setIdentificaties(List<Identificatie> identificaties) {
        this.identificaties.clear();
        this.identificaties.addAll(identificaties);
    }

    /**
     * Deterministische keuze uit de identificaties, voor wanneer precies één identificatie nodig
     * is. Sorteert op type dan nummer; die volgorde is stabiel maar draagt verder geen betekenis
     * (geen "primaire" identificatie in domeinzin). {@code null} als de partij geen identificaties
     * heeft (invariant violation, zie findOrCreatePartij).
     */
    public Identificatie primaireIdentificatie() {
        return identificaties.stream()
                .min(Comparator.comparing((Identificatie i) -> i.getIdentificatieType().name())
                        .thenComparing(Identificatie::getIdentificatieNummer))
                .orElse(null);
    }

    public List<Contactgegeven> getContactgegevens() {
        return Collections.unmodifiableList(contactgegevens);
    }

    public void setContactgegevens(List<Contactgegeven> contactgegevens) {
        this.contactgegevens.clear();
        this.contactgegevens.addAll(contactgegevens);
    }
}
