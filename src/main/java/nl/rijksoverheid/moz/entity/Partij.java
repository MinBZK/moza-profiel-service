package nl.rijksoverheid.moz.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
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

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Identificatie> identificaties = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contactgegeven> contactgegevens = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Voorkeur> voorkeuren = new ArrayList<>();

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

    public void removeVoorkeur(Voorkeur voorkeur) {
        voorkeuren.remove(voorkeur);
    }

    public List<Identificatie> getIdentificaties() {
        return Collections.unmodifiableList(identificaties);
    }

    public void setIdentificaties(List<Identificatie> identificaties) {
        this.identificaties.clear();
        this.identificaties.addAll(identificaties);
    }

    public List<Contactgegeven> getContactgegevens() {
        return Collections.unmodifiableList(contactgegevens);
    }

    public void setContactgegevens(List<Contactgegeven> contactgegevens) {
        this.contactgegevens.clear();
        this.contactgegevens.addAll(contactgegevens);
    }

    public void removeContactgegeven(Contactgegeven contact) {
        contactgegevens.remove(contact);
    }
}
