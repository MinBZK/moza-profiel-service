package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Scope extends PanacheEntity {

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "partij_id")
    @Nullable
    private Partij partij;

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "dienst_id")
    @Nullable
    private Dienst dienst;

    // A scope is owned by exactly one of contactgegeven or voorkeur. The owner is set via
    // Contactgegeven.addScope or Voorkeur.addScope, which guarantees the XOR invariant by construction.
    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "contactgegeven_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Nullable
    private Contactgegeven contactgegeven;

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "voorkeur_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Nullable
    private Voorkeur voorkeur;

    @Nullable
    public Partij getPartij() {
        return partij;
    }

    public void setPartij(@Nullable Partij partij) {
        this.partij = partij;
    }

    @Nullable
    public Dienst getDienst() {
        return dienst;
    }

    public void setDienst(@Nullable Dienst dienst) {
        this.dienst = dienst;
    }

    @Nullable
    public Contactgegeven getContactgegeven() {
        return contactgegeven;
    }

    public void setContactgegeven(@Nullable Contactgegeven contactgegeven) {
        this.contactgegeven = contactgegeven;
    }

    @Nullable
    public Voorkeur getVoorkeur() {
        return voorkeur;
    }

    public void setVoorkeur(@Nullable Voorkeur voorkeur) {
        this.voorkeur = voorkeur;
    }
}
