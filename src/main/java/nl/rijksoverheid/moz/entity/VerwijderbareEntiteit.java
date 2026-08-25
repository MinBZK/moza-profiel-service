package nl.rijksoverheid.moz.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.Nullable;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

// Public: OngefilterdeFinderTest (ander package) verwijst rechtstreeks naar deze klasse. Geen
// @SQLRestriction: dat is niet per query uit te zetten, en meerdere tests inspecteren juist een
// soft deleted rij rechtstreeks.
@MappedSuperclass
public abstract class VerwijderbareEntiteit extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Nullable
    private Instant verwijderdOp;

    @Nullable
    public Instant getVerwijderdOp() {
        return verwijderdOp;
    }

    public boolean isVerwijderd() {
        return verwijderdOp != null;
    }

    // Gooit i.p.v. stil te negeren: een tweede aanroep binnen dezelfde persistence context wijst op
    // een programmeerfout. Beschermt niet tegen een race tussen twee transacties — de guard leest het
    // in-memory veld, niet de rij; zie PartijService.lockEnLeesVerwijderdOp voor waar dat wél nodig en
    // afgedwongen is.
    public void verwijder(Instant nu) {
        Objects.requireNonNull(nu, "nu mag niet null zijn");

        if (verwijderdOp != null) {
            throw new IllegalStateException(getClass().getSimpleName() + " " + id + " is al verwijderd op " + verwijderdOp);
        }

        verwijderdOp = nu;
    }
}
