package nl.rijksoverheid.moz.services;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import nl.rijksoverheid.moz.clients.verificatie_service.api.VerificationControllerApi;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.logging.Logger;

@ApplicationScoped
public class Startup {

    private static final Logger LOGGER = Logger.getLogger("StartUpBean");

    @Inject
    @RestClient
    VerificationControllerApi emailVerificatieApi;

    void onStart(@Observes StartupEvent ev) {
        LOGGER.info("""
                :
                 ____            __ _      _   ____                  _
                |  _ \\ _ __ ___ / _(_) ___| | / ___|  ___ _ ____   _(_) ___ ___
                | |_) | '__/ _ \\ |_| |/ _ \\ | \\___ \\ / _ \\ '__\\ \\ / / |/ __/ _ \\
                |  __/| | | (_) |  _| |  __/ |  ___) |  __/ |   \\ V /| | (_|  __/
                |_|   |_|  \\___/|_| |_|\\___|_| |____/ \\___|_|    \\_/ |_|\\___\\___|
                """);

        LOGGER.info("Using: " + emailVerificatieApi.getClass());

    }
}