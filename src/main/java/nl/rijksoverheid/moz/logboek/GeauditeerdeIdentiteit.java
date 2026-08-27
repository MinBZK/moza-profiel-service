package nl.rijksoverheid.moz.logboek;

import nl.rijksoverheid.moz.common.IdentificatieType;

import java.util.Objects;

// Alleen de scalaire velden, niet de entity zelf: na commit is de persistence context die de
// entity beheerde mogelijk al gesloten, en deze twee waarden zijn het enige dat de
// afterCompletion-callback nodig heeft.
public record GeauditeerdeIdentiteit(String identificatieNummer, IdentificatieType identificatieType) {

    // Faalt luid i.p.v. een vermelding zonder dataSubjectId door te laten glippen.
    public GeauditeerdeIdentiteit {
        Objects.requireNonNull(identificatieNummer, "identificatieNummer mag niet null zijn");
        Objects.requireNonNull(identificatieType, "identificatieType mag niet null zijn");
    }
}
