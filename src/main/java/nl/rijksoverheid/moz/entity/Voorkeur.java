package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import nl.rijksoverheid.moz.common.VoorkeurType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Audited
@Table(indexes = @Index(name = "idx_voorkeur_dedup", columnList = "partij_id, voorkeurType, waarde"))
public class Voorkeur extends PanacheEntity implements Scoped {

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
    private List<Scope> scopes = new ArrayList<>();

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

    public List<Scope> getScopes() {
        return Collections.unmodifiableList(scopes);
    }

    public void addScope(Scope scope) {
        scopes.add(scope);
        scope.setVoorkeur(this);
    }

    public void clearScopes() {
        scopes.clear();
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
