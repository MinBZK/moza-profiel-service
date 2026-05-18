package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.*;
import nl.rijksoverheid.moz.common.ContactType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Audited
@Table(indexes = @Index(name = "idx_contactgegeven_dedup", columnList = "partij_id, type, waarde"))
public class Contactgegeven extends PanacheEntity implements Scoped {

    @JsonIgnore
    @ManyToOne
    @NotNull
    private Partij partij;

    @OneToMany(mappedBy = "contactgegeven", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 32)
    private List<Scope> scopes = new ArrayList<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    private ContactType type;

    @NotNull
    private String waarde;

    @Nullable
    private LocalDateTime geverifieerdAt;

    @Nullable
    private String verificatieReferentieId;

    private boolean isGeverifieerd = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUpdated;

    @Nullable
    private LocalDateTime lastUsedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        lastUpdated = now;
    }

    @PreUpdate
    private void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    public Partij getPartij() {
        return partij;
    }

    public void setPartij(Partij partij) {
        this.partij = partij;
    }

    public List<Scope> getScopes() {
        return Collections.unmodifiableList(scopes);
    }

    public void addScope(Scope scope) {
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

    public boolean isIsGeverifieerd() {
        return isGeverifieerd;
    }

    public void setIsGeverifieerd(boolean isGeverifieerd) {
        this.isGeverifieerd = isGeverifieerd;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    @Nullable
    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(@Nullable LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
