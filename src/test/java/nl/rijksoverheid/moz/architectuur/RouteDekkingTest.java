package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Bewaakt dat het contract en de JAX-RS-routes elkaar dekken, in beide richtingen.
 *
 * <p>Sinds annotatie-scanning uit staat ({@code mp.openapi.scan.disable=true}) wordt het
 * gepubliceerde document niet meer uit de code afgeleid. {@code OpenApiContractDriftTest}
 * vergelijkt dat document met {@code META-INF/openapi.yaml}, maar dat zijn sindsdien twee
 * verwijzingen naar hetzelfde bestand: die test bewaakt de configuratie, niet de vraag of het
 * contract nog beschrijft wat de service doet. Een nieuw endpoint zonder contractwijziging — of
 * een {@code @Path} die verdwijnt terwijl de operatie blijft staan — komt daar niet uit.
 *
 * <p>Deze test leest daarom de routes rechtstreeks uit de resource-klassen en legt ze naast de
 * operaties in het contract. Hij vergelijkt pad en HTTP-methode, niet de vorm van request of
 * response; dat laatste doet de contractvalidatie in {@code OpenApiValidationTest} per aanroep.
 */
class RouteDekkingTest {

    /**
     * Alle HTTP-methoden die JAX-RS kent, ook de methoden die dit contract vandaag niet gebruikt.
     * Zo valt een resource-methode die er ooit bijkomt niet stilzwijgend buiten de sweep.
     */
    private static final String CONTROLLER_PAKKET = "nl.rijksoverheid.moz.controller";

    private static final Map<Class<? extends Annotation>, String> HTTP_METHODEN = Map.of(
            GET.class, "get",
            POST.class, "post",
            PUT.class, "put",
            PATCH.class, "patch",
            DELETE.class, "delete",
            HEAD.class, "head",
            OPTIONS.class, "options");

    @Test
    void contractEnRoutesDekkenElkaar() throws Exception {
        TreeSet<String> routes = new TreeSet<>(routesUitDeCode());
        TreeSet<String> operaties = new TreeSet<>(operatiesUitHetContract());

        Assertions.assertFalse(routes.isEmpty(),
                "Geen enkele JAX-RS-route gevonden; deze test controleert dan niets");

        List<String> ongedocumenteerd = new ArrayList<>(routes);
        ongedocumenteerd.removeAll(operaties);

        List<String> zonderRoute = new ArrayList<>(operaties);
        zonderRoute.removeAll(routes);

        List<String> bevindingen = new ArrayList<>();

        if (!ongedocumenteerd.isEmpty()) {
            bevindingen.add("Deze routes staan niet in META-INF/openapi.yaml: " + ongedocumenteerd);
        }

        if (!zonderRoute.isEmpty()) {
            bevindingen.add("Deze operaties staan in het contract maar hebben geen resource-methode: "
                    + zonderRoute);
        }

        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
    }

    private static List<String> routesUitDeCode() {
        // Het hele applicatiepakket, niet alleen .controller: een resource die ooit elders komt te
        // staan zou anders buiten de sweep vallen zonder dat iets dat meldt.
        JavaClasses klassen = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("nl.rijksoverheid.moz");

        List<String> routes = new ArrayList<>();

        for (JavaClass klasse : klassen) {
            if (!klasse.isAnnotatedWith(Path.class)) {
                continue;
            }

            // Interfaces buiten het controllerpakket overslaan. @Path staat in deze codebase ook
            // op REST-clients: de gegenereerde VerificationControllerApi voor de externe
            // verificatieservice draagt @Path("") op klasseniveau — dát is wat hem hier zou
            // binnenhalen — en @Path("/request") en @Path("/verify") op zijn methoden. Die
            // beschrijven wat wij áánroepen, niet wat wij aanbieden.
            //
            // De uitzondering is bewust smal. Interfaces overal overslaan zou een handgeschreven
            // JAX-RS interface met een implementatie zonder eigen @Path onzichtbaar maken, in
            // beide richtingen. Binnen het controllerpakket telt een interface dus gewoon mee.
            if (klasse.isInterface() && !klasse.getPackageName().equals(CONTROLLER_PAKKET)) {
                continue;
            }

            String basisPad = klasse.getAnnotationOfType(Path.class).value();

            for (JavaMethod methode : klasse.getMethods()) {
                HTTP_METHODEN.forEach((annotatie, naam) -> {
                    if (!methode.isAnnotatedWith(annotatie)) {
                        return;
                    }

                    String deelPad = methode.isAnnotatedWith(Path.class)
                            ? methode.getAnnotationOfType(Path.class).value()
                            : "";

                    routes.add(naam + " " + normaliseer(basisPad + "/" + deelPad));
                });
            }
        }

        return routes;
    }

    private static List<String> operatiesUitHetContract() throws Exception {
        JsonNode paden;

        try (InputStream in = RouteDekkingTest.class.getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            paden = new ObjectMapper(new YAMLFactory()).readTree(in).path("paths");
        }

        Assertions.assertTrue(paden.isObject() && !paden.isEmpty(),
                "paths ontbreekt in het contract of is geen object");

        List<String> operaties = new ArrayList<>();
        var padNamen = paden.fieldNames();

        while (padNamen.hasNext()) {
            String pad = padNamen.next();
            var methodeNamen = paden.get(pad).fieldNames();

            while (methodeNamen.hasNext()) {
                String methode = methodeNamen.next();

                // parameters, summary en servers mogen naast de operaties op een pad staan.
                if (HTTP_METHODEN.containsValue(methode)) {
                    operaties.add(methode + " " + normaliseer(pad));
                }
            }
        }

        return operaties;
    }

    /**
     * JAX-RS is onverschillig voor dubbele en afsluitende slashes — {@code @Path("/dienstverlener/")}
     * bedient hetzelfde pad als {@code /dienstverlener} — terwijl het contract één schrijfwijze
     * per pad kent. Zonder deze normalisatie zou de test op die kosmetiek omvallen in plaats van
     * op echte drift.
     */
    private static String normaliseer(String pad) {
        String samengevouwen = pad.replaceAll("/{2,}", "/");

        if (samengevouwen.length() > 1 && samengevouwen.endsWith("/")) {
            return samengevouwen.substring(0, samengevouwen.length() - 1);
        }

        return samengevouwen;
    }
}
