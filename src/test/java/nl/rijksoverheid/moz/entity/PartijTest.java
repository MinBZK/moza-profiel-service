package nl.rijksoverheid.moz.entity;

import nl.rijksoverheid.moz.common.IdentificatieType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
