package nl.rijksoverheid.moz.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "RFC 7807 application/problem+json foutmelding")
public record ProblemDetail(
        @Schema(description = "URI die het type probleem identificeert", example = "https://mijnoverheidzakelijk.nl/errors/not-found")
        String type,
        @Schema(description = "Korte, leesbare titel die het probleem beschrijft", example = "Not Found")
        String title,
        @Schema(description = "HTTP-statuscode die bij het probleem hoort", example = "404")
        int status,
        @Schema(description = "Gedetailleerde beschrijving van het probleem")
        String detail,
        @Schema(description = "URI die verwijst naar de specifieke instantie van het probleem")
        String instance
) {
}
