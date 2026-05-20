package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.envers.Audited;

import java.util.UUID;

@Entity
@Audited
@Table(
        name = "dienstverlener_dienst",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dienstverlener_dienst",
                columnNames = {"dienstverlener_id", "dienst_id"}
        )
)
public class DienstverlenerDienst extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "dienstverlener_id")
    @JsonIgnore
    private Dienstverlener dienstverlener;

    @Nullable
    @ManyToOne
    @JoinColumn(name = "dienst_id")
    @JsonIgnore
    private Dienst dienst;

    public DienstverlenerDienst() {
    }

    public DienstverlenerDienst(Dienstverlener dienstverlener, @Nullable Dienst dienst) {
        this.dienstverlener = dienstverlener;
        this.dienst = dienst;
    }

    public Dienstverlener getDienstverlener() {
        return dienstverlener;
    }

    public void setDienstverlener(Dienstverlener dienstverlener) {
        this.dienstverlener = dienstverlener;
    }

    @Nullable
    public Dienst getDienst() {
        return dienst;
    }

    public void setDienst(@Nullable Dienst dienst) {
        this.dienst = dienst;
    }
}
