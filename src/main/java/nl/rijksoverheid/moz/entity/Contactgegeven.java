package nl.rijksoverheid.moz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import static nl.rijksoverheid.moz.entity.SoftDeleteFilters.ACTIEF;

// Geen @UniqueConstraint voor uk_contactgegeven_dedup: die index is partieel (WHERE
// verwijderd_op IS NULL, zie de V4-migratie) en JPA kan geen WHERE-clausule uitdrukken.
@Entity
@Audited
public class Contactgegeven extends VerwijderbareEntiteit {

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
    public static Contactgegeven find(Partij partij, UUID id) {
        return find("partij = ?1 AND id = ?2 AND " + ACTIEF, partij, id).firstResult();
    }

    @Nullable
    public static Contactgegeven find(Partij partij, ContactType type, String waarde) {
        return find("partij = ?1 AND type = ?2 AND waarde = ?3 AND " + ACTIEF, partij, type, waarde).firstResult();
    }

    public static List<Contactgegeven> find(Partij partij) {
        return find("partij = ?1 AND " + ACTIEF, partij).list();
    }

    // Sluit exceptId uit zodat een update niet als duplicaat van zichzelf geldt. exceptId moet
    // niet-null zijn: met null matcht "id <> ?4" geen enkele rij (SQL: NULL <> x is unknown), dus
    // exists() geeft dan altijd stilzwijgend false terug.
    public static boolean exists(Partij partij, ContactType type, String waarde, UUID exceptId) {
        return find(
                "partij = ?1 AND type = ?2 AND waarde = ?3 AND id <> ?4 AND " + ACTIEF,
                partij, type, waarde, exceptId
        ).firstResultOptional().isPresent();
    }

    // Filtert soft deletes weg: een verwijderd e-mailadres is niet meer te verifiëren.
    @Nullable
    public static Contactgegeven findEmail(Partij partij, String email) {
        return find(
                "partij = ?1 AND type = ?2 AND LOWER(waarde) = LOWER(?3) AND " + ACTIEF,
                partij, ContactType.Email, email
        ).firstResult();
    }

    // Aanroeper moet dit vóór eigen setType/setIsDefault draaien (voorkomt twee tijdelijk-actieve
    // isDefault-rijen). lastUpdated expliciet meegebumped: bulk-update bypasst @PreUpdate.
    public static long demoteDefault(Partij partij, ContactType type, UUID exceptId, Instant nu) {
        return update(
                "isDefault = false, lastUpdated = ?1 WHERE partij = ?2 AND type = ?3 AND isDefault = true AND " + ACTIEF + " AND id <> ?4",
                nu, partij, type, exceptId);
    }

    // ACTIEF + rowcount: een GET die overlapt met een retentie-soft-delete van dezelfde rij mag
    // lastUsedAt niet meer bumpen.
    public static long touch(UUID id, Instant nu) {
        return update("lastUsedAt = ?1 WHERE id = ?2 AND " + ACTIEF, nu, id);
    }
}
