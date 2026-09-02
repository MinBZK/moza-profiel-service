package nl.rijksoverheid.moz;

import io.quarkus.narayana.jta.QuarkusTransaction;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;

/**
 * The suite shares one Quarkus boot and one schema, so rows left behind leak into
 * whatever runs next. Keeping the entity list here rather than copied into every
 * test class stops the copies drifting apart when an entity is added.
 */
public final class DatabaseCleanup {

    private DatabaseCleanup() {
    }

    /** Deletes every row, children before parents. */
    public static void wipe() {
        QuarkusTransaction.requiringNew().run(() -> {
            ScopeContactgegeven.deleteAll();
            ScopeVoorkeur.deleteAll();
            Contactgegeven.deleteAll();
            Voorkeur.deleteAll();
            DienstverlenerDienst.deleteAll();
            Dienst.deleteAll();
            Identificatie.deleteAll();
            Partij.deleteAll();
            Dienstverlener.deleteAll();
        });
    }
}
