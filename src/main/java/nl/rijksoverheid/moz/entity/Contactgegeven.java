package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.ContactType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Audited
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_contactgegeven_dedup",
                columnNames = {"partij_id", "type", "waarde"}
        )
)
public class Contactgegeven extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "partij_id")
    @NotNull
    private Partij partij;

    @OneToMany(mappedBy = "contactgegeven", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 32)
    private List<ScopeContactgegeven> scopes = new ArrayList<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    private ContactType type;

    @NotNull
    private String waarde;

    private boolean isGeverifieerd = false;

    @Nullable
    private Instant geverifieerdAt;

    @Nullable
    private String verificatieReferentieId;

    private boolean isDefault = false;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant lastUpdated;

    @Nullable
    private Instant lastUsedAt;

    @Nullable
    private Instant teVerwijderenOp;

    private boolean teVerwijderenOpAutomatisch = false;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        lastUpdated = now;
    }

    @PreUpdate
    private void onUpdate() {
        lastUpdated = Instant.now();
    }


    public Partij getPartij() {
        return partij;
    }

    public void setPartij(Partij partij) {
        this.partij = partij;
    }

    public List<ScopeContactgegeven> getScopes() {
        return Collections.unmodifiableList(scopes);
    }

    public void addScope(ScopeContactgegeven scope) {
        scopes.add(scope);
        scope.setContactgegeven(this);
    }

    public void clearScopes() {
        scopes.clear();
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

    public boolean isIsGeverifieerd() {
        return isGeverifieerd;
    }

    public void setIsGeverifieerd(boolean isGeverifieerd) {
        this.isGeverifieerd = isGeverifieerd;
    }

    @Nullable
    public Instant getGeverifieerdAt() {
        return geverifieerdAt;
    }

    public void setGeverifieerdAt(@Nullable Instant geverifieerdAt) {
        this.geverifieerdAt = geverifieerdAt;
    }

    @Nullable
    public String getVerificatieReferentieId() {
        return verificatieReferentieId;
    }

    public void setVerificatieReferentieId(@Nullable String verificatieReferentieId) {
        this.verificatieReferentieId = verificatieReferentieId;
    }

    public boolean isIsDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    @Nullable
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(@Nullable Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    @Nullable
    public Instant getTeVerwijderenOp() {
        return teVerwijderenOp;
    }

    public void setTeVerwijderenOp(@Nullable Instant teVerwijderenOp) {
        this.teVerwijderenOp = teVerwijderenOp;
    }

    public boolean isTeVerwijderenOpAutomatisch() {
        return teVerwijderenOpAutomatisch;
    }

    public void setTeVerwijderenOpAutomatisch(boolean teVerwijderenOpAutomatisch) {
        this.teVerwijderenOpAutomatisch = teVerwijderenOpAutomatisch;
    }
}
