package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;

import java.util.UUID;

@Entity
@Audited
@Table(
        name = "scope_voorkeur",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scope_voorkeur",
                columnNames = {"voorkeur_id", "dienstverlener_dienst_id"}
        )
)
public class ScopeVoorkeur extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "voorkeur_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private Voorkeur voorkeur;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "dienstverlener_dienst_id")
    @JsonIgnore
    private DienstverlenerDienst dienstverlenerDienst;

    public ScopeVoorkeur() {
    }

    public ScopeVoorkeur(Voorkeur voorkeur, DienstverlenerDienst dienstverlenerDienst) {
        this.voorkeur = voorkeur;
        this.dienstverlenerDienst = dienstverlenerDienst;
    }

    public Voorkeur getVoorkeur() {
        return voorkeur;
    }

    public void setVoorkeur(Voorkeur voorkeur) {
        this.voorkeur = voorkeur;
    }

    public DienstverlenerDienst getDienstverlenerDienst() {
        return dienstverlenerDienst;
    }

    public void setDienstverlenerDienst(DienstverlenerDienst dienstverlenerDienst) {
        this.dienstverlenerDienst = dienstverlenerDienst;
    }
}
