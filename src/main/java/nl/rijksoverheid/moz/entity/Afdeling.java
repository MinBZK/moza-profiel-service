package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.ContactType;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Audited
public class Afdeling extends PanacheEntity {

    @NotNull
    private String beschrijving; //TODO Iets unieks gaan gebruiken om afdeling aan contactgegeven te koppelen want dienstverleners kunnen afdeling naam delen

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "dienstverlener_id")
    private Dienstverlener dienstverlener;

    @OneToMany(mappedBy = "afdeling")
    private List<Contactgegeven> contactgegevens = new ArrayList<>();

    public static Afdeling findByBeschrijving(@NotNull String afdeling) {
        return Afdeling.find("beschrijving = ?1", afdeling).firstResult();
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

    public List<Contactgegeven> getContactgegevens() {
        return contactgegevens;
    }

    public void setContactgegevens(List<Contactgegeven> contactgegevens) {
        this.contactgegevens = contactgegevens;
    }
}
