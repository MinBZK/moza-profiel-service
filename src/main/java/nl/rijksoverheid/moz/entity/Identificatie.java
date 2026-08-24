package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.hibernate.envers.Audited;

import java.util.UUID;

// uk_identificatie (identificatie_type, identificatie_nummer) en uk_identificatie_per_partij
// (partij_id, identificatie_type) bestaan in de database (zie V4-migratie) als partiële unique
// indexes (WHERE verwijderd_op IS NULL). JPA's @UniqueConstraint kan geen WHERE-clausule
// uitdrukken, dus staan ze hier bewust niet als annotatie — zie Contactgegeven voor dezelfde
// afweging.
@Entity
@Audited
public class Identificatie extends VerwijderbareEntiteit {

    @Id
    @GeneratedValue
    public UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    private IdentificatieType identificatieType;

    @NotNull
    private String identificatieNummer;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "partij_id")
    private Partij partij;

    public Identificatie(IdentificatieType identificatieType, String identificatieNummer) {
        this.identificatieType = identificatieType;
        this.identificatieNummer = identificatieNummer;
    }

    public Identificatie() {
    }

    public IdentificatieType getIdentificatieType() {
        return identificatieType;
    }

    public void setIdentificatieType(IdentificatieType identificatieType) {
        this.identificatieType = identificatieType;
    }

    public String getIdentificatieNummer() {
        return identificatieNummer;
    }

    public void setIdentificatieNummer(String identificatieNummer) {
        this.identificatieNummer = identificatieNummer;
    }

    public Partij getPartij() {
        return partij;
    }

    public void setPartij(Partij partij) {
        this.partij = partij;
    }
}
