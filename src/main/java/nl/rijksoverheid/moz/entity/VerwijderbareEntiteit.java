package nl.rijksoverheid.moz.entity;

import java.time.Instant;
import java.util.Objects;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.Nullable;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class VerwijderbareEntiteit extends PanacheEntityBase {

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
            throw new IllegalStateException(getClass().getSimpleName() + " " + entiteitId() + " is al verwijderd op " + verwijderdOp);
        }

        verwijderdOp = nu;
    }

    abstract Object entiteitId();
}
