package nl.rijksoverheid.moz.architectuur.fixtures;

import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;

import java.util.function.Consumer;

/**
 * Elke bekende manier om een binnenkomend request te muteren, één per klasse, zodat
 * {@code RegelDekkingTest} per vorm kan aantonen dát de regels hem pakken.
 *
 * <p>Deze klassen zijn geen tests en worden nergens aangeroepen; ze bestaan om als
 * {@code JavaClasses} te worden ingelezen. Ze staan in testbronnen, dus de regels zoals die
 * tegen productiecode draaien ({@code DoNotIncludeTests}) zien ze niet.
 */
public final class RequestMutaties {

    private RequestMutaties() {
    }

    /** De gewone setter. Naam valt buiten de allow-list, dus de naamregel pakt hem. */
    public static final class DirecteSetter {
        public void muteer(ContactgegevenUpdateRequest request) {
            request.setWaarde("gewijzigd");
        }
    }

    /** Fluent setter met de naam van de property; naam valt eveneens buiten de allow-list. */
    public static final class FluentSetter {
        public void muteer(ContactgegevenUpdateRequest request) {
            request.waarde("gewijzigd");
        }
    }

    /**
     * Fluent setter op een property die zelf {@code is}-achtig heet. De naam matcht
     * {@code is[A-Z].*} en glipt dus door de naamregel; alleen de ariteitsregel vangt hem.
     */
    public static final class FluentSetterMetGetterNaam {
        public void muteer(ContactgegevenUpdateRequest request) {
            request.isDefault(Boolean.TRUE);
        }
    }

    /**
     * Method reference naar een gewone setter: geen JavaCall, alleen een toegang. De method
     * reference is hier de testvorm en niet een omweg — vervang hem niet door een directe
     * aanroep, want dan wordt deze fixture een duplicaat van {@link DirecteSetter} en verdwijnt
     * de dekking zonder dat een test omvalt.
     */
    public static final class MethodReferenceNaarSetter {
        public void muteer(ContactgegevenUpdateRequest request) {
            Consumer<String> zet = request::setWaarde;
            zet.accept("gewijzigd");
        }
    }

    /**
     * De lastigste combinatie: getter-achtige naam én method reference. Zie
     * {@link MethodReferenceNaarSetter} voor waarom de method reference moet blijven staan; hier
     * zou een directe aanroep deze fixture gelijk maken aan {@link FluentSetterMetGetterNaam}.
     */
    public static final class MethodReferenceNaarFluentSetterMetGetterNaam {
        public void muteer(ContactgegevenUpdateRequest request) {
            Consumer<Boolean> zet = request::isDefault;
            zet.accept(Boolean.TRUE);
        }
    }

    /**
     * Negatieve controle: puur lezen hoort door geen enkele regel te worden gevlagd.
     *
     * <p>De {@code equals}-aanroep staat er met opzet bij. Hij is de enige uitzondering in de
     * allow-list van de ariteitsregel, en zonder een aanroeper zou een kapotte allow-list
     * onzichtbaar blijven: geen enkele fixture en geen productiecode raakt hem anders.
     */
    public static final class AlleenLezen {
        public String lees(ContactgegevenUpdateRequest request, ContactgegevenUpdateRequest andere) {
            return request.getWaarde() + request.getIsDefault() + request.hashCode()
                    + request.equals(andere);
        }
    }
}
