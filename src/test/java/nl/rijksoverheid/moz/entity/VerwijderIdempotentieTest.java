package nl.rijksoverheid.moz.entity;

import nl.rijksoverheid.moz.common.IdentificatieType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * verwijder(Instant) op elk van de vier soft-deletable entiteiten belooft idempotentie: een al
 * gezette verwijderdOp blijft staan. De candidate-queries filteren zelf al op verwijderdOp IS
 * NULL, dus een test die via zo'n query een al-verwijderde rij oppikt bewijst alleen die filter,
 * nooit deze guard — vandaar een losse, directe test per entiteit.
 */
class VerwijderIdempotentieTest {

    private static final Instant EERSTE = Instant.now().minus(3, ChronoUnit.DAYS);
    private static final Instant TWEEDE = Instant.now();

    @Test
    void contactgegeven_verwijderTweeKeer_HoudtEersteWaarde() {
        Contactgegeven cg = new Contactgegeven();
        cg.verwijder(EERSTE);
        cg.verwijder(TWEEDE);

        Assertions.assertEquals(EERSTE, cg.getVerwijderdOp());
    }

    @Test
    void voorkeur_verwijderTweeKeer_HoudtEersteWaarde() {
        Voorkeur voorkeur = new Voorkeur();
        voorkeur.verwijder(EERSTE);
        voorkeur.verwijder(TWEEDE);

        Assertions.assertEquals(EERSTE, voorkeur.getVerwijderdOp());
    }

    @Test
    void partij_verwijderTweeKeer_HoudtEersteWaarde() {
        Partij partij = new Partij();
        partij.verwijder(EERSTE);
        partij.verwijder(TWEEDE);

        Assertions.assertEquals(EERSTE, partij.getVerwijderdOp());
    }

    @Test
    void identificatie_verwijderTweeKeer_HoudtEersteWaarde() {
        Identificatie identificatie = new Identificatie(IdentificatieType.BSN, "123456789");
        identificatie.verwijder(EERSTE);
        identificatie.verwijder(TWEEDE);

        Assertions.assertEquals(EERSTE, identificatie.getVerwijderdOp());
    }
}
