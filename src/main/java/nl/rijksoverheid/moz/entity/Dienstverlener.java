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
    private String oin; //TODO koppelen aan identificatie tabel


    @OneToMany(mappedBy = "dienstverlener", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Afdeling> afdelingen = new ArrayList<>();


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

    public List<Afdeling> getAfdelingen() {
        return Collections.unmodifiableList(afdelingen);
    }

    public void addAfdeling(Afdeling afdeling) {
        afdelingen.add(afdeling);
    }

    public void setAfdelingen(List<Afdeling> afdelingen) {
        this.afdelingen.clear();
        this.afdelingen.addAll(afdelingen);
    }
}
