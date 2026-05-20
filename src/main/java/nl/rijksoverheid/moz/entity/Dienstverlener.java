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
public class Dienstverlener extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    // Globaal uniek, case-insensitief. Afgedwongen door een functional unique index
    // (lower(naam)) in de Flyway-migratie; niet door een Hibernate @Column(unique=true)
    // omdat dat geen functional index kan uitdrukken.
    @NotNull
    private String naam;

    @Nullable
    private String beschrijving;

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
