package nl.rijksoverheid.moz.mapper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.rijksoverheid.moz.api.generated.model.DienstResponse;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

@QuarkusTest
class DienstMapperTest {

    @Inject
    DienstMapper dienstMapper;

    private static Dienst dienst(String naam, String beschrijving) {
        Dienst dienst = new Dienst();
        dienst.id = UUID.randomUUID();
        dienst.setNaam(naam);
        dienst.setBeschrijving(beschrijving);
        return dienst;
    }

    private static Dienstverlener dienstverlener(String naam, String beschrijving) {
        Dienstverlener dv = new Dienstverlener();
        dv.id = UUID.randomUUID();
        dv.setNaam(naam);
        dv.setBeschrijving(beschrijving);
        return dv;
    }

    @Test
    void toDienstResponse_maptAlleVelden() {
        Dienst dienst = dienst("Aanvraag vergunning", "Beschrijving");

        DienstResponse response = dienstMapper.toDienstResponse(dienst);

        Assertions.assertEquals(dienst.id, response.getId());
        Assertions.assertEquals("Aanvraag vergunning", response.getNaam());
        Assertions.assertEquals("Beschrijving", response.getBeschrijving());
    }

    @Test
    void toDienstResponse_beschrijvingMagNullZijn() {
        DienstResponse response = dienstMapper.toDienstResponse(dienst("Dienst zonder beschrijving", null));

        Assertions.assertNull(response.getBeschrijving());
    }

    @Test
    void toDienstResponse_nullInput_geeftNull() {
        Assertions.assertNull(dienstMapper.toDienstResponse(null));
    }

    @Test
    void toDienstverlenerResponse_maptDienstverlenerEnDiensten() {
        Dienstverlener dv = dienstverlener("Gemeente Amsterdam", "Beschrijving DV");
        Dienst dienstA = dienst("Dienst A", "A");
        Dienst dienstB = dienst("Dienst B", null);

        DienstverlenerResponse response = dienstMapper.toDienstverlenerResponse(dv, List.of(dienstA, dienstB));

        Assertions.assertEquals("Gemeente Amsterdam", response.getNaam());
        Assertions.assertEquals("Beschrijving DV", response.getBeschrijving());
        Assertions.assertEquals(2, response.getDiensten().size());
        Assertions.assertEquals(dienstA.id, response.getDiensten().get(0).getId());
        Assertions.assertEquals("Dienst A", response.getDiensten().get(0).getNaam());
        Assertions.assertEquals("Dienst B", response.getDiensten().get(1).getNaam());
        Assertions.assertNull(response.getDiensten().get(1).getBeschrijving());
    }

    @Test
    void toDienstverlenerResponse_legeDienstenlijst_geeftLegeLijst() {
        DienstverlenerResponse response =
                dienstMapper.toDienstverlenerResponse(dienstverlener("Dienstverlener zonder diensten", null), List.of());

        Assertions.assertEquals("Dienstverlener zonder diensten", response.getNaam());
        Assertions.assertNotNull(response.getDiensten());
        Assertions.assertTrue(response.getDiensten().isEmpty());
    }
}
