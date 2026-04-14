package nl.rijksoverheid.moz.dto.response;

import java.util.UUID;

public class KoppelcodeResponse {
    public UUID koppelcode;

    public KoppelcodeResponse() {
    }

    public KoppelcodeResponse(UUID koppelcode) {
        this.koppelcode = koppelcode;
    }
}
