package nl.rijksoverheid.moz.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import nl.rijksoverheid.moz.api.generated.api.DienstverlenerApi;
import nl.rijksoverheid.moz.api.generated.model.DienstRequest;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerRequest;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.mapper.DienstMapper;
import nl.rijksoverheid.moz.services.DienstverlenerService;
import org.jboss.logging.Logger;

import java.net.URI;

/**
 * REST controller voor dienstverleners. Contract-first (#651, #751): implementeert de uit
 * META-INF/openapi.yaml gegenereerde {@link DienstverlenerApi}, die de paden, HTTP-methodes,
 * mediatypes en de validatie van de body-parameter draagt. Die annotaties mogen hier niet
 * herhaald worden: draagt een implementatiemethode ook maar één JAX-RS-annotatie, dan negeert
 * JAX-RS die van de interface volledig (JAX-RS 3.1 §3.6), waardoor een half overgenomen set
 * de route stilzwijgend onbereikbaar maakt. Een parameterconstraint opnieuw declareren is
 * zelfs een harde fout: dat laat de applicatie niet meer starten (HV000151).
 */
public class DienstverlenerController implements DienstverlenerApi {

    private static final Logger LOG = Logger.getLogger(DienstverlenerController.class);

    private final DienstverlenerService dienstverlenerService;
    private final DienstMapper dienstMapper;

    public DienstverlenerController(DienstverlenerService dienstverlenerService, DienstMapper dienstMapper) {
        this.dienstverlenerService = dienstverlenerService;
        this.dienstMapper = dienstMapper;
    }

    @Override
    public Response getDienstverlener(String naam) {
        Dienstverlener dv = dienstverlenerService.getDienstverlener(naam);

        if (dv == null) {
            LOG.warn("Dienstverlener niet gevonden");
            throw Problems.notFound("Dienstverlener niet gevonden", "Geen dienstverlener gevonden met de opgegeven naam.");
        }

        DienstverlenerResponse response = dienstMapper.toDienstverlenerResponse(dv, dienstverlenerService.getDienstenVoorDienstverlener(dv));
        LOG.info("Dienstverlener opgehaald");
        return Response.ok(response).build();
    }

    @Override
    @Transactional
    public Response addDienstverlener(DienstverlenerRequest dienstverlenerRequest) {
        Dienstverlener created = dienstverlenerService.addDienstverlener(dienstverlenerRequest);

        LOG.info("Dienstverlener toegevoegd");
        // Het pad komt uit de gegenereerde interface, zodat de Location-header het contract volgt.
        URI uri = UriBuilder.fromResource(DienstverlenerApi.class)
                .path("{naam}")
                .build(created.getNaam());
        // Niet List.of(): bestond de dienstverlener al, dan zou het antwoord beweren dat hij
        // geen diensten heeft terwijl GET op dezelfde resource ze wel teruggeeft.
        DienstverlenerResponse body = dienstMapper.toDienstverlenerResponse(
                created, dienstverlenerService.getDienstenVoorDienstverlener(created));
        return Response.created(uri).entity(body).build();
    }

    @Override
    @Transactional
    public Response addDienstToDienstverlener(String dienstverlenerNaam, DienstRequest dienstRequest) {
        // Een lege body is al door RequireBodyReaderInterceptor afgewezen.
        Dienst created = dienstverlenerService.addDienstToDienstverlener(dienstverlenerNaam, dienstRequest);
        LOG.info("Dienst toegevoegd aan dienstverlener");
        URI uri = UriBuilder.fromResource(DienstverlenerApi.class)
                .path("{dienstverlenerNaam}").path("diensten").path("{id}")
                .build(dienstverlenerNaam, created.id);
        return Response.created(uri).entity(dienstMapper.toDienstResponse(created)).build();
    }
}
