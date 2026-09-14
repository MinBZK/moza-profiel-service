package nl.rijksoverheid.moz.entity;

import nl.rijksoverheid.moz.common.IdentificatieType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class PartijTest {

    @Test
    void primaireIdentificatie_BsnEnKvk_KiestBsn() {
        Partij partij = new Partij();
        partij.addIdentificatie(new Identificatie(IdentificatieType.KVK, "12345678"));
        partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));

        Assertions.assertEquals(IdentificatieType.BSN, partij.primaireIdentificatie().getIdentificatieType());
    }

    @Test
    void primaireIdentificatie_KvkEnRsin_KiestKvk() {
        Partij partij = new Partij();
        partij.addIdentificatie(new Identificatie(IdentificatieType.RSIN, "123456789"));
        partij.addIdentificatie(new Identificatie(IdentificatieType.KVK, "12345678"));

        Assertions.assertEquals(IdentificatieType.KVK, partij.primaireIdentificatie().getIdentificatieType());
    }

    @Test
    void primaireIdentificatie_EnkeleIdentificatie_KiestDieEne() {
        Partij partij = new Partij();
        partij.addIdentificatie(new Identificatie(IdentificatieType.RSIN, "123456789"));

        Assertions.assertEquals(IdentificatieType.RSIN, partij.primaireIdentificatie().getIdentificatieType());
    }

    @Test
    void primaireIdentificatie_GeenIdentificaties_GeeftNull() {
        Partij partij = new Partij();

        Assertions.assertNull(partij.primaireIdentificatie());
    }

    @Test
    void primaireIdentificatie_VerwijderdeBsnMetLagerNummer_KiestDeActieveBsn() {
        // Zonder de soft-delete-filter zou de comparator (type, dan nummer) de verwijderde BSN
        // "100000000" kiezen — lager nummer — boven de actieve "999999999". Dat zou een
        // verwijderde identiteit als AVG-dataSubject in het logboek zetten.
        Partij partij = new Partij();
        Identificatie verwijderdeBsn = new Identificatie(IdentificatieType.BSN, "100000000");
        verwijderdeBsn.verwijder(Instant.now());
        partij.addIdentificatie(verwijderdeBsn);
        Identificatie actieveBsn = new Identificatie(IdentificatieType.BSN, "999999999");
        partij.addIdentificatie(actieveBsn);

        Assertions.assertEquals(actieveBsn, partij.primaireIdentificatie());
    }

    @Test
    void primaireIdentificatie_AlleIdentificatiesVerwijderd_GeeftNull() {
        Partij partij = new Partij();
        Identificatie verwijderd = new Identificatie(IdentificatieType.BSN, "123456789");
        verwijderd.verwijder(Instant.now());
        partij.addIdentificatie(verwijderd);

        Assertions.assertNull(partij.primaireIdentificatie());
    }
}
