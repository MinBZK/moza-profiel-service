package nl.rijksoverheid.moz.entity;
import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import nl.rijksoverheid.moz.common.IdentificatieType;
import org.hibernate.envers.Audited;

@Entity
@Table(uniqueConstraints=@UniqueConstraint(name="uk_identificatie", columnNames={"identificatieType","identificatieNummer"}))
@Audited
public class Partij extends PanacheEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    private IdentificatieType identificatieType;

    @NotNull
    private String identificatieNummer;

    @OneToMany(mappedBy = "eigenaarPartij", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Nullable
    private List<Contactvoorkeur> eigenaar;

    @OneToMany(mappedBy = "betreffendePartij", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Nullable
    private List<Contactvoorkeur> betreffende;

    public static Partij findByIdentificatieNummer(IdentificatieType identificatieType, String identificatieNummer) {
        return find("identificatieType = ?1 and identificatieNummer = ?2", identificatieType, identificatieNummer).firstResult();
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

    @Nullable
    public List<Contactvoorkeur> getEigenaar() {
        return eigenaar;
    }

    public void setEigenaar(@Nullable List<Contactvoorkeur> eigenaar) {
        this.eigenaar = eigenaar;
    }

    @Nullable
    public List<Contactvoorkeur> getBetreffende() {
        return betreffende;
    }

    public void setBetreffende(@Nullable List<Contactvoorkeur> betreffende) {
        this.betreffende = betreffende;
    }
}
