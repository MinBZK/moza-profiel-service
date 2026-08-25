package nl.rijksoverheid.moz.logboek;

import nl.rijksoverheid.moz.common.IdentificatieType;

// Alleen de scalaire velden, niet de entity zelf: na commit is de persistence context die de
// entity beheerde mogelijk al gesloten, en deze twee waarden zijn het enige dat de
// afterCompletion-callback nodig heeft.
public record GeauditeerdeIdentiteit(String identificatieNummer, IdentificatieType identificatieType) {
}
