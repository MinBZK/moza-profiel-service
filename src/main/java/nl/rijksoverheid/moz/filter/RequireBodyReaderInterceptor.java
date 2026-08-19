package nl.rijksoverheid.moz.filter;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;

/**
 * Wijst een lege request body af zodra die is gedeserialiseerd, maar vóórdat de
 * resource-methode draait. Daardoor wordt voor zo'n verzoek geen LDV-span
 * aangemaakt: er zijn immers geen persoonsgegevens verwerkt, dus hoort het niet
 * in het logboek thuis. De fout rendert als application/problem+json (RFC 9457).
 *
 * <p>De check kijkt naar het gedeserialiseerde resultaat in plaats van naar
 * transport-headers (Content-Length): dat dekt zowel een ontbrekende body als een
 * letterlijke {@code null} body.
 *
 * <p>Bewust zonder name binding. Sinds de resources via gegenereerde interfaces zijn
 * gedeclareerd (#751) leest JAX-RS de name binding van de interface, niet van de
 * implementatie, waardoor een {@code @RequireBody} op de controller niets meer deed. Een
 * binding is hier ook niet nodig: op een endpoint zónder body-parameter draait er geen
 * {@code MessageBodyReader} en dus ook deze interceptor niet, en elke request body die wél
 * gelezen wordt staat in het contract als {@code required: true}. Komt er ooit een endpoint
 * met een optionele body, dan moet dit opnieuw worden afgewogen: die krijgt hier nu
 * stilzwijgend een 400.
 *
 * <p>{@code @ConstrainedTo(SERVER)} is nodig omdat een provider zonder binding anders ook
 * de responses van de REST-clients leest. Een upstream die 200 met een JSON-{@code null}
 * teruggeeft zou dan een fout over een ontbrekende *request* body opleveren — een melding
 * die naar de verkeerde kant van de aanroep wijst.
 *
 * <p>De gegenereerde interfaces dragen {@code @NotNull} op elke body-parameter, wat bij een
 * lege body eveneens een 400 oplevert. Deze interceptor blijft daarnaast bestaan omdat die
 * {@code @NotNull} pas ná de {@code @Logboek}-interceptor vuurt: dan is de LDV-span al
 * geopend voor een verzoek waarin geen persoonsgegevens zijn verwerkt. Bovendien geeft dit
 * pad een leesbare melding in plaats van een violations-lijst met een leeg veld.
 */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class RequireBodyReaderInterceptor implements ReaderInterceptor {

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        Object body = context.proceed();

        if (body == null) {
            throw HttpProblem.valueOf(Response.Status.BAD_REQUEST, "Request body mag niet leeg zijn");
        }

        return body;
    }
}
