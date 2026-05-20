package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;

import java.util.UUID;

@Entity
@Audited
@Table(
        name = "scope_contactgegeven",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scope_contactgegeven",
                columnNames = {"contactgegeven_id", "dienstverlener_dienst_id"}
        )
)
public class ScopeContactgegeven extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "contactgegeven_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private Contactgegeven contactgegeven;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "dienstverlener_dienst_id")
    @JsonIgnore
    private DienstverlenerDienst dienstverlenerDienst;

    public ScopeContactgegeven() {
    }

    public ScopeContactgegeven(Contactgegeven contactgegeven, DienstverlenerDienst dienstverlenerDienst) {
        this.contactgegeven = contactgegeven;
        this.dienstverlenerDienst = dienstverlenerDienst;
    }

    public Contactgegeven getContactgegeven() {
        return contactgegeven;
    }

    public void setContactgegeven(Contactgegeven contactgegeven) {
        this.contactgegeven = contactgegeven;
    }

    public DienstverlenerDienst getDienstverlenerDienst() {
        return dienstverlenerDienst;
    }

    public void setDienstverlenerDienst(DienstverlenerDienst dienstverlenerDienst) {
        this.dienstverlenerDienst = dienstverlenerDienst;
    }
}
