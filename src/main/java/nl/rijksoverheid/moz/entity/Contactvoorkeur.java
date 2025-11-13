package nl.rijksoverheid.moz.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.*;
import nl.rijksoverheid.moz.common.ContactvoorkeurType;
import nl.rijksoverheid.moz.common.Scope;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
public class Contactvoorkeur extends PanacheEntity {

    @ManyToOne
    @NotNull
    private Partij eigenaarPartij;

    @ManyToOne
    @Nullable
    private Partij betreffendePartij;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Scope scope;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ContactvoorkeurType type;

    @NotNull
    private String waarde;

    @Nullable
    private LocalDateTime geverifieerdAt;

    @Nullable
    private String dienstType;

    public Partij getEigenaarPartij() {
        return eigenaarPartij;
    }

    public void setEigenaarPartij(Partij eigenaarPartij) {
        this.eigenaarPartij = eigenaarPartij;
    }

    @Nullable
    public Partij getBetreffendePartij() {
        return betreffendePartij;
    }

    public void setBetreffendePartij(@Nullable Partij betreffendePartij) {
        this.betreffendePartij = betreffendePartij;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public ContactvoorkeurType getType() {
        return type;
    }

    public void setType(ContactvoorkeurType type) {
        this.type = type;
    }

    public String getWaarde() {
        return waarde;
    }

    public void setWaarde(String waarde) {
        this.waarde = waarde;
    }

    @Nullable
    public LocalDateTime getGeverifieerdAt() {
        return geverifieerdAt;
    }

    public void setGeverifieerdAt(@Nullable LocalDateTime geverifieerdAt) {
        this.geverifieerdAt = geverifieerdAt;
    }

    @Nullable
    public String getDienstType() {
        return dienstType;
    }

    public void setDienstType(@Nullable String dienstType) {
        this.dienstType = dienstType;
    }
}
