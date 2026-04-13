package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.VoorkeurType;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Voorkeur extends PanacheEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    private VoorkeurType voorkeurType;

    @NotNull
    private String waarde;

    @ManyToOne(optional = false)
    @JoinColumn(name = "partij_id")
    @JsonIgnore // prevent infinite loop
    private Partij partij;

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "afdeling_id")
    @Nullable
    private Afdeling afdeling;

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "scope_partij_id")
    @Nullable
    private Partij scopePartij;

    public VoorkeurType getVoorkeurType() {
        return voorkeurType;
    }

    public void setVoorkeurType(VoorkeurType voorkeurType) {
        this.voorkeurType = voorkeurType;
    }

    public String getWaarde() {
        return waarde;
    }

    public void setWaarde(String waarde) {
        this.waarde = waarde;
    }

    public Partij getPartij() {
        return partij;
    }

    public void setPartij(Partij partij) {
        this.partij = partij;
    }

    @Nullable
    public Afdeling getAfdeling() {
        return afdeling;
    }

    public void setAfdeling(@Nullable Afdeling afdeling) {
        this.afdeling = afdeling;
    }

    @Nullable
    public Partij getScopePartij() {
        return scopePartij;
    }

    public void setScopePartij(@Nullable Partij scopePartij) {
        this.scopePartij = scopePartij;
    }
}


