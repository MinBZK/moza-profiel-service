package nl.rijksoverheid.moz;

import jakarta.ws.rs.core.UriInfo;
import org.mockito.Mockito;

/**
 * {@code Problems.problemResponse} leest alleen het pad uit {@link UriInfo}. Die afhankelijkheid
 * staat hier op één plek, zodat ze bij een wijziging op één plek breekt.
 */
public final class UriInfoStub {

    private UriInfoStub() {}

    /** Het pad zoals {@code UriInfo.getPath()} het geeft: gedecodeerd en zonder root-path. */
    public static UriInfo voorPad(String pad) {
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getPath()).thenReturn(pad);
        return uriInfo;
    }
}
