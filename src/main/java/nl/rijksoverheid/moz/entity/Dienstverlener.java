package nl.rijksoverheid.moz.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Audited
public class Dienstverlener extends PanacheEntity {

    @NotNull
    private String naam;

    @NotNull
    private String oin;

    @OneToMany(mappedBy = "dienstverlener", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Dienst> diensten = new ArrayList<>();

    public String getNaam() {
        return naam;
    }

    public void setNaam(String naam) {
        this.naam = naam;
    }

    public String getOin() {
        return oin;
    }

    public void setOin(String oin) {
        this.oin = oin;
    }

    public List<Dienst> getDiensten() {
        return Collections.unmodifiableList(diensten);
    }

    public void addDienst(Dienst dienst) {
        diensten.add(dienst);
    }

    public void setDiensten(List<Dienst> diensten) {
        this.diensten.clear();
        this.diensten.addAll(diensten);
    }
}
