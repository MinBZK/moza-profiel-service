package nl.rijksoverheid.moz.mapper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.rijksoverheid.moz.dto.response.DienstResponse;
import nl.rijksoverheid.moz.dto.response.DienstverlenerResponse;
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

        Assertions.assertEquals(dienst.id, response.id);
        Assertions.assertEquals("Aanvraag vergunning", response.naam);
        Assertions.assertEquals("Beschrijving", response.beschrijving);
    }

    @Test
    void toDienstResponse_beschrijvingMagNullZijn() {
        DienstResponse response = dienstMapper.toDienstResponse(dienst("Dienst zonder beschrijving", null));

        Assertions.assertNull(response.beschrijving);
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

        Assertions.assertEquals("Gemeente Amsterdam", response.naam);
        Assertions.assertEquals("Beschrijving DV", response.beschrijving);
        Assertions.assertEquals(2, response.diensten.size());
        Assertions.assertEquals(dienstA.id, response.diensten.get(0).id);
        Assertions.assertEquals("Dienst A", response.diensten.get(0).naam);
        Assertions.assertEquals("Dienst B", response.diensten.get(1).naam);
        Assertions.assertNull(response.diensten.get(1).beschrijving);
    }

    @Test
    void toDienstverlenerResponse_legeDienstenlijst_geeftLegeLijst() {
        DienstverlenerResponse response =
                dienstMapper.toDienstverlenerResponse(dienstverlener("Dienstverlener zonder diensten", null), List.of());

        Assertions.assertEquals("Dienstverlener zonder diensten", response.naam);
        Assertions.assertNotNull(response.diensten);
        Assertions.assertTrue(response.diensten.isEmpty());
    }
}
