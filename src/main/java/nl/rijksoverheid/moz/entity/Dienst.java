package nl.rijksoverheid.moz.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import org.hibernate.envers.Audited;

import java.util.UUID;

@Entity
@Audited
public class Dienst extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @NotNull
    private String naam;

    @Nullable
    private String beschrijving;

    public static Dienst findByNaam(@NotNull String naam) {
        return Dienst.find("lower(naam) = lower(?1)", naam).firstResult();
    }

    public String getNaam() {
        return naam;
    }

    public void setNaam(String naam) {
        this.naam = naam;
    }

    @Nullable
    public String getBeschrijving() {
        return beschrijving;
    }

    public void setBeschrijving(@Nullable String beschrijving) {
        this.beschrijving = beschrijving;
    }
}
