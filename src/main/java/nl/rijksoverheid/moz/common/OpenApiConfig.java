package nl.rijksoverheid.moz.common;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                title = "MOZa Profiel Service API",
                version = "1.0.0",
                description = "Profiel service voor MijnOverheid Zakelijk",
                contact = @Contact(
                        name = "MijnOverheid Zakelijk Team",
                        email = "moza@minbzk.nl",
                        url = "https://github.com/MijnOverheid-Zakelijk/moza-profiel-service/issues"
                ),
                license = @License(
                        name = "EUPL-1.2",
                        url = "https://joinup.ec.europa.eu/software/page/eupl"
                )
        ),
        servers = {
                @Server(url = "https://profiel.mijnoverheidzakelijk.nl/api/profielservice/v1", description = "Productie"),
                @Server(url = "http://localhost:8080/api/profielservice/v1", description = "Lokale ontwikkeling")
        }
)
public class OpenApiConfig extends Application {
}
