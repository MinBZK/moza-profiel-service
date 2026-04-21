package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.*;
import nl.rijksoverheid.moz.common.ContactType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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
    @JoinColumn(name = "scope_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Nullable
    private Scope scope;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ContactType type;

    @NotNull
    private String waarde;

    @Nullable
    private LocalDateTime geverifieerdAt;

    @Nullable
    private String verificatieReferentieId;

    private boolean nogSteedsValide = false;

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

    @Nullable
    public Scope getScope() {
        return scope;
    }

    public void setScope(@Nullable Scope scope) {
        this.scope = scope;
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

    public boolean isNogSteedsValide() {
        return nogSteedsValide;
    }

    public void setNogSteedsValide(boolean nogSteedsValide) {
        this.nogSteedsValide = nogSteedsValide;
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
