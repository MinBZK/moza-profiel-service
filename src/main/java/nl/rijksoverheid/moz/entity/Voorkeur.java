package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
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
}


