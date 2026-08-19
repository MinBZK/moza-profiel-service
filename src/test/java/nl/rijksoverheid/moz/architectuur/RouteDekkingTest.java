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
import jakarta.ws.rs.PathParam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bewaakt dat het contract en de JAX-RS-routes elkaar dekken, in beide richtingen: pad en
 * HTTP-methode, niet de vorm van de berichten. {@code OpenApiContractDriftTest} kan dit niet —
 * die vergelijkt het gepubliceerde document met het bestand waaruit het komt.
 */
class RouteDekkingTest {

    private static final String CONTROLLER_PAKKET = "nl.rijksoverheid.moz.controller";

    /** De uit het contract gegenereerde server-interfaces die de controllers implementeren (#751). */
    private static final String SERVER_API_PAKKET = "nl.rijksoverheid.moz.api.generated.api";

    /** JAX-RS staat een reguliere expressie toe achter de naam: {@code {id: [0-9]+}}. */
    private static final Pattern PAD_PARAMETER = Pattern.compile("\\{\\s*([^}:\\s]+)\\s*(?::[^}]*)?}");

    /**
     * Alle HTTP-methoden die JAX-RS kent, ook de methoden die dit contract vandaag niet gebruikt.
     * Zo valt een resource-methode die er ooit bijkomt niet stilzwijgend buiten de sweep.
     */
    private static final Map<Class<? extends Annotation>, String> HTTP_METHODEN = Map.of(
            GET.class, "get",
            POST.class, "post",
            PUT.class, "put",
            PATCH.class, "patch",
            DELETE.class, "delete",
            HEAD.class, "head",
            OPTIONS.class, "options");

    /**
     * Een resource-methode met haar route: de eigenaar staat erbij zodat een dubbele mapping te
     * herleiden is, en om een geërfde methode die onder twee klassen opduikt één keer te tellen.
     */
    private record Route(String route, String eigenaar, List<String> padParameters,
                         List<String> pathParams) {
    }

    @Test
    void contractEnRoutesDekkenElkaar() throws Exception {
        TreeSet<String> routes = new TreeSet<>(routesUitDeCode().stream().map(Route::route).toList());
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

    /**
     * Twee resource-methoden op dezelfde methode + pad is voor JAX-RS een dubbelzinnige mapping;
     * het contract kan zo'n paar niet beschrijven, want daar is één operatie per combinatie.
     */
    @Test
    void geenTweeResourceMethodenOpDezelfdeRoute() {
        Map<String, List<String>> perRoute = new TreeMap<>();

        for (Route route : routesUitDeCode()) {
            perRoute.computeIfAbsent(route.route(), sleutel -> new ArrayList<>()).add(route.eigenaar());
        }

        List<String> dubbel = perRoute.entrySet().stream()
                .filter(entry -> new TreeSet<>(entry.getValue()).size() > 1)
                .map(entry -> entry.getKey() + " -> " + new TreeSet<>(entry.getValue()))
                .toList();

        Assertions.assertTrue(dubbel.isEmpty(),
                "Deze routes zijn door meer dan één resource-methode gemapt: " + dubbel);
    }

    /**
     * Het pad en de {@code @PathParam}-namen worden apart geschreven; loopt er één uit de pas, dan
     * blijft het pad gelijk aan het contract terwijl de parameter niet meer gebonden wordt.
     */
    @Test
    void padParametersEnPathParamsDragenDezelfdeNamen() {
        List<String> bevindingen = new ArrayList<>();

        for (Route route : routesUitDeCode()) {
            if (!new TreeSet<>(route.padParameters()).equals(new TreeSet<>(route.pathParams()))) {
                bevindingen.add(route.eigenaar() + " (" + route.route() + ") heeft padparameters "
                        + new TreeSet<>(route.padParameters()) + " en @PathParam-namen "
                        + new TreeSet<>(route.pathParams()));
            }
        }

        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
    }

    private static List<Route> routesUitDeCode() {
        // Het hele applicatiepakket, niet alleen .controller: een resource die ooit elders komt te
        // staan zou anders buiten de sweep vallen zonder dat iets dat meldt.
        JavaClasses klassen = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("nl.rijksoverheid.moz");

        // Elke interface die door een concrete klasse geïmplementeerd wordt. Een gegenereerde
        // server-interface telt alleen als route wanneer hij hierin voorkomt. Zonder die eis
        // vergelijkt deze test het contract met zichzelf: beide zijden komen dan uit
        // openapi.yaml. Een operatie onder een nieuwe tag levert dan een interface op die
        // niemand implementeert, waarna de build slaagt, deze test groen blijft en het endpoint
        // in productie een 404 geeft.
        Set<String> geimplementeerdeInterfaces = klassen.stream()
                .filter(klasse -> !klasse.isInterface())
                .flatMap(klasse -> klasse.getAllRawInterfaces().stream())
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        Map<String, Route> routes = new TreeMap<>();

        for (JavaClass klasse : klassen) {
            if (!klasse.isAnnotatedWith(Path.class)) {
                continue;
            }

            // Onze routes staan sinds #751 op de gegenereerde server-interfaces, die de
            // controllers implementeren; die tellen mee zodra er een implementatie is, net als
            // een handgeschreven interface in het controllerpakket. Andere interfaces met @Path
            // zijn REST-clients: de gegenereerde VerificationControllerApi draagt @Path("") en
            // beschrijft wat wij áánroepen, niet wat wij aanbieden.
            if (klasse.isInterface()
                    && !klasse.getPackageName().equals(CONTROLLER_PAKKET)
                    && !(klasse.getPackageName().equals(SERVER_API_PAKKET)
                            && geimplementeerdeInterfaces.contains(klasse.getName()))) {
                continue;
            }

            String basisPad = klasse.getAnnotationOfType(Path.class).value();

            // getAllMethods() en niet getMethods(): dat laatste geeft alleen de gedeclareerde
            // methoden, waardoor een geërfde resource-methode buiten de sweep zou vallen. Een
            // override levert de methode twee keer op; de sleutel hieronder telt hem één keer.
            for (JavaMethod methode : klasse.getAllMethods()) {
                HTTP_METHODEN.forEach((annotatie, naam) -> {
                    if (!methode.isAnnotatedWith(annotatie)) {
                        return;
                    }

                    String deelPad = methode.isAnnotatedWith(Path.class)
                            ? methode.getAnnotationOfType(Path.class).value()
                            : "";

                    String pad = normaliseer(basisPad + "/" + deelPad);
                    String eigenaar = methode.getOwner().getSimpleName() + "#" + methode.getName();

                    routes.put(eigenaar + " " + naam + " " + pad,
                            new Route(naam + " " + pad, eigenaar, padParameters(pad), pathParams(methode)));
                });
            }
        }

        return List.copyOf(routes.values());
    }

    private static List<String> padParameters(String pad) {
        List<String> namen = new ArrayList<>();
        Matcher treffer = PAD_PARAMETER.matcher(pad);

        while (treffer.find()) {
            namen.add(treffer.group(1));
        }

        return namen;
    }

    private static List<String> pathParams(JavaMethod methode) {
        return methode.getParameters().stream()
                .filter(parameter -> parameter.isAnnotatedWith(PathParam.class))
                .map(parameter -> parameter.getAnnotationOfType(PathParam.class).value())
                .toList();
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
     * JAX-RS is onverschillig voor dubbele en afsluitende slashes, het contract kent één
     * schrijfwijze per pad. Zonder normalisatie valt de test op die kosmetiek om.
     */
    private static String normaliseer(String pad) {
        String samengevouwen = pad.replaceAll("/{2,}", "/");

        if (samengevouwen.length() > 1 && samengevouwen.endsWith("/")) {
            return samengevouwen.substring(0, samengevouwen.length() - 1);
        }

        return samengevouwen;
    }
}
