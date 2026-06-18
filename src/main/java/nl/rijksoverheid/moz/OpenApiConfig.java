package nl.rijksoverheid.moz;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.servers.Server;

/**
 * OpenAPI metadata for the profiel-service.
 *
 * Satisfies ADR /core/doc-openapi-contact (info.contact with name, email, url)
 * and ADR /core/uri-version (major version on the server URL, not in the path).
 * Spectral rules `nlgov:info-contact-fields-exist` and `nlgov:semver` require this.
 *
 * This class does NOT extend `jakarta.ws.rs.core.Application`; doing so would
 * activate JAX-RS Application-class scanning and require an `@ApplicationPath`,
 * which would re-prefix every controller's existing `@Path("/api/profielservice/v1")`.
 * smallrye-openapi picks up `@OpenAPIDefinition` from any class.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "MOZa Profiel Service API",
        version = ApiVersion.CURRENT,
        description = "Profiel service voor MijnOverheid Zakelijk: beheer van contactgegevens en communicatievoorkeuren van partijen.",
        contact = @Contact(
            name = "MijnOverheid Zakelijk Team",
            email = "moza@minbzk.nl",
            url = "https://docs.mijnoverheidzakelijk.nl"
        ),
        license = @License(
            name = "EUPL-1.2",
            url = "https://joinup.ec.europa.eu/software/page/eupl"
        )
    ),
    servers = {
        @Server(url = "https://api.mijnoverheidzakelijk.nl/profielservice", description = "Productie"),
        @Server(url = "http://localhost:8080", description = "Lokale ontwikkelomgeving")
    }
)
public class OpenApiConfig {
}
