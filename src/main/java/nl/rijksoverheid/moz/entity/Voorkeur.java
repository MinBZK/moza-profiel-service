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
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.VoorkeurType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Audited
public class Voorkeur extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    private VoorkeurType voorkeurType;

    @NotNull
    private String waarde;

    @ManyToOne(optional = false)
    @JoinColumn(name = "partij_id")
    @JsonIgnore
    private Partij partij;

    @OneToMany(mappedBy = "voorkeur", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 32)
    private List<ScopeVoorkeur> scopes = new ArrayList<>();

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

    public List<ScopeVoorkeur> getScopes() {
        return Collections.unmodifiableList(scopes);
    }

    public void addScope(ScopeVoorkeur scope) {
        scopes.add(scope);
        scope.setVoorkeur(this);
    }

    public void clearScopes() {
        scopes.clear();
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
