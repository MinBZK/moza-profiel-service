package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.hibernate.envers.Audited;

import java.util.UUID;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identificatie",
                columnNames = {"identificatie_type", "identificatie_nummer"}
        )
)
@Audited
public class Identificatie extends PanacheEntityBase {

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
