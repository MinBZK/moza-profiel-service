package nl.rijksoverheid.moz.entity;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Partij extends PanacheEntity {

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Identificatie> identificaties = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contactgegeven> contactgegevens = new ArrayList<>();

    @OneToMany(mappedBy = "partij", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Voorkeur> voorkeuren = new ArrayList<>();

    public List<Voorkeur> getVoorkeuren() {
        return voorkeuren;
    }

    public void setVoorkeuren(List<Voorkeur> voorkeuren) {
        this.voorkeuren = voorkeuren;
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

    public List<Identificatie> getIdentificaties() {
        return identificaties;
    }

    public void setIdentificaties(List<Identificatie> identificaties) {
        this.identificaties = identificaties;
    }

    public List<Contactgegeven> getContactgegevens() {
        return contactgegevens;
    }

    public void setContactgegevens(List<Contactgegeven> contactgegevens) {
        this.contactgegevens = contactgegevens;
    }
}

