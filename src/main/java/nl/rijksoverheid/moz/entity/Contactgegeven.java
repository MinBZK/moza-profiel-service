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
import nl.rijksoverheid.moz.common.ContactType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// uk_contactgegeven_dedup (partij_id, type, waarde) bestaat in de database (zie V1/V4-migraties),
// maar is daar een partiële unique index (WHERE verwijderd_op IS NULL). JPA's @UniqueConstraint
// kan geen WHERE-clausule uitdrukken, dus staat die hier bewust niet: een niet-partiële variant
// via deze annotatie zou in de door Hibernate gegenereerde testschema's (H2, drop-and-create)
// weer duplicaten tegen zachtverwijderde rijen blokkeren, terwijl productie dat toestaat.
@Entity
@Audited
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
    private Instant verwijderdOp;

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
    public Instant getVerwijderdOp() {
        return verwijderdOp;
    }

    public void setVerwijderdOp(Instant verwijderdOp) {
        this.verwijderdOp = verwijderdOp;
    }

    @Nullable
    public static Contactgegeven findActiefById(Partij partij, UUID id) {
        return find("partij = ?1 AND id = ?2 AND verwijderdOp IS NULL", partij, id).firstResult();
    }

    @Nullable
    public static Contactgegeven findActiefById(UUID id) {
        return find("id = ?1 AND verwijderdOp IS NULL", id).firstResult();
    }

    @Nullable
    public static Contactgegeven findActief(Partij partij, ContactType type, String waarde) {
        return find("partij = ?1 AND type = ?2 AND waarde = ?3 AND verwijderdOp IS NULL", partij, type, waarde).firstResult();
    }

    public static List<Contactgegeven> findActief(Partij partij) {
        return find("partij = ?1 AND verwijderdOp IS NULL", partij).list();
    }

    public static boolean existsActief(Partij partij, ContactType type, String waarde, UUID exceptId) {
        return find(
                "partij = ?1 AND type = ?2 AND waarde = ?3 AND id <> ?4 AND verwijderdOp IS NULL",
                partij, type, waarde, exceptId
        ).firstResultOptional().isPresent();
    }

    /** Case-insensitieve match op e-mailadres, voor verificatie-lookups. */
    @Nullable
    public static Contactgegeven findActiefEmail(Partij partij, String email) {
        return find(
                "partij = ?1 AND type = ?2 AND LOWER(waarde) = LOWER(?3) AND verwijderdOp IS NULL",
                partij, ContactType.Email, email
        ).firstResult();
    }
}
