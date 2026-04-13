package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.*;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.Taal;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
public class Contactgegeven extends PanacheEntity {

    @JsonIgnore
    @ManyToOne
    @NotNull
    private Partij partij;

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "afdeling_id")
    private Afdeling afdeling;

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "scope_partij_id")
    @Nullable
    private Partij scopePartij;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ContactType type;

    @NotNull
    private String waarde;

    @Nullable
    @Enumerated(EnumType.STRING)
    private Taal taal;

    @Nullable
    private String terAttentieVan;

    @Nullable
    private LocalDateTime geverifieerdAt;

    @Nullable
    private String verificatieReferentieId;

    public Partij getPartij() {
        return partij;
    }

    public void setPartij(Partij partij) {
        this.partij = partij;
    }

    public Afdeling getAfdeling() {
        return afdeling;
    }

    public void setAfdeling(Afdeling afdeling) {
        this.afdeling = afdeling;
    }

    public ContactType getType() {
        return type;
    }

    public void setType(ContactType type) {
        this.type = type;
    }

    public String getWaarde() {
        return waarde;
    }

    public void setWaarde(String waarde) {
        this.waarde = waarde;
    }

    @Nullable
    public Partij getScopePartij() {
        return scopePartij;
    }

    public void setScopePartij(@Nullable Partij scopePartij) {
        this.scopePartij = scopePartij;
    }

    @Nullable
    public Taal getTaal() {
        return taal;
    }

    public void setTaal(@Nullable Taal taal) {
        this.taal = taal;
    }

    @Nullable
    public String getTerAttentieVan() {
        return terAttentieVan;
    }

    public void setTerAttentieVan(@Nullable String terAttentieVan) {
        this.terAttentieVan = terAttentieVan;
    }

    @Nullable
    public LocalDateTime getGeverifieerdAt() {
        return geverifieerdAt;
    }

    public void setGeverifieerdAt(@Nullable LocalDateTime geverifieerdAt) {
        this.geverifieerdAt = geverifieerdAt;
    }

    @Nullable
    public String getVerificatieReferentieId() {
        return verificatieReferentieId;
    }

    public void setVerificatieReferentieId(@Nullable String verificatieReferentieId) {
        this.verificatieReferentieId = verificatieReferentieId;
    }
}
