package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

@Entity
@Audited
public class Dienst extends PanacheEntity {

    @NotNull
    private String beschrijving;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "dienstverlener_id")
    private Dienstverlener dienstverlener;

    @OneToMany(mappedBy = "dienst")
    private List<Scope> scopes = new ArrayList<>();

    public static Dienst findByBeschrijving(@NotNull String beschrijving) {
        return Dienst.find("beschrijving = ?1", beschrijving).firstResult();
    }

    public String getBeschrijving() {
        return beschrijving;
    }

    public void setBeschrijving(String beschrijving) {
        this.beschrijving = beschrijving;
    }

    public Dienstverlener getDienstverlener() {
        return dienstverlener;
    }

    public void setDienstverlener(Dienstverlener dienstverlener) {
        this.dienstverlener = dienstverlener;
    }

    public List<Scope> getScopes() {
        return scopes;
    }

    public void setScopes(List<Scope> scopes) {
        this.scopes = scopes;
    }
}
