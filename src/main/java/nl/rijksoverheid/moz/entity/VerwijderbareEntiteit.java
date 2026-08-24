package nl.rijksoverheid.moz.entity;

import java.time.Instant;

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

    // Gooit i.p.v. stil te negeren: een tweede aanroep wijst op een racecondition of
    // programmeerfout die eerder in de aanroepketen al voorkomen had moeten worden.
    public void verwijder(Instant nu) {
        if (verwijderdOp != null) {
            throw new IllegalStateException(getClass().getSimpleName() + " is al verwijderd op " + verwijderdOp);
        }

        verwijderdOp = nu;
    }
}
