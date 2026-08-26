package nl.rijksoverheid.moz.entity;

import nl.rijksoverheid.moz.common.IdentificatieType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * verwijder(Instant) op elk van de vier soft-deletable entiteiten gooit IllegalStateException op
 * een tweede aanroep i.p.v. de tweede waarde stil te negeren. De candidate-queries filteren zelf al
 * op verwijderdOp IS NULL, dus een test die via zo'n query een al-verwijderde rij oppikt bewijst
 * alleen die filter, nooit deze guard — vandaar een losse, directe test per entiteit.
 */
class VerwijderGuardTest {

    private static final Instant EERSTE = Instant.now().minus(3, ChronoUnit.DAYS);
    private static final Instant TWEEDE = Instant.now();

    @Test
    void contactgegeven_verwijderTweeKeer_GooitException() {
        Contactgegeven cg = new Contactgegeven();
        cg.verwijder(EERSTE);

        Assertions.assertThrows(IllegalStateException.class, () -> cg.verwijder(TWEEDE));
        Assertions.assertEquals(EERSTE, cg.getVerwijderdOp());
    }

    @Test
    void voorkeur_verwijderTweeKeer_GooitException() {
        Voorkeur voorkeur = new Voorkeur();
        voorkeur.verwijder(EERSTE);

        Assertions.assertThrows(IllegalStateException.class, () -> voorkeur.verwijder(TWEEDE));
        Assertions.assertEquals(EERSTE, voorkeur.getVerwijderdOp());
    }

    @Test
    void partij_verwijderTweeKeer_GooitException() {
        Partij partij = new Partij();
        partij.verwijder(EERSTE);

        Assertions.assertThrows(IllegalStateException.class, () -> partij.verwijder(TWEEDE));
        Assertions.assertEquals(EERSTE, partij.getVerwijderdOp());
    }

    @Test
    void identificatie_verwijderTweeKeer_GooitException() {
        Identificatie identificatie = new Identificatie(IdentificatieType.BSN, "123456789");
        identificatie.verwijder(EERSTE);

        Assertions.assertThrows(IllegalStateException.class, () -> identificatie.verwijder(TWEEDE));
        Assertions.assertEquals(EERSTE, identificatie.getVerwijderdOp());
    }
}
