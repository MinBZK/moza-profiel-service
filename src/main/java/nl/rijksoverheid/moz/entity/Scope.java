package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Scope extends PanacheEntity {

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "partij_id")
    @Nullable
    private Partij partij;

    @JsonIgnore
    @ManyToOne(optional = true)
    @JoinColumn(name = "dienst_id")
    @Nullable
    private Dienst dienst;

    @Nullable
    public Partij getPartij() {
        return partij;
    }

    public void setPartij(@Nullable Partij partij) {
        this.partij = partij;
    }

    @Nullable
    public Dienst getDienst() {
        return dienst;
    }

    public void setDienst(@Nullable Dienst dienst) {
        this.dienst = dienst;
    }
}
