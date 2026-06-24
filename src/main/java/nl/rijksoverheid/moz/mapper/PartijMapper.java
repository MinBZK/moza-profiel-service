package nl.rijksoverheid.moz.mapper;

import nl.rijksoverheid.moz.api.generated.model.ContactgegevenResponse;
import nl.rijksoverheid.moz.api.generated.model.IdentificatieResponse;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
import nl.rijksoverheid.moz.api.generated.model.ScopeResponse;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurResponse;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Mapt {@link Partij}-entiteiten naar hun response-DTO's.
 * <p>
 * Het zuivere veld-voor-veld kopiëren wordt door MapStruct gegenereerd. De methodes
 * {@link #toContactgegevensResponse(Contactgegeven)} en {@link #toVoorkeurResponse(Voorkeur)}
 * bevatten daarnaast retentie-logica ("touch on read"): wanneer een gegeven lang niet is
 * gebruikt wordt {@code lastUsedAt} bijgewerkt en een automatisch gezette verwijderdatum
 * teruggedraaid. Die business-logica is bewust handgeschreven en delegeert het kopiëren
 * naar de door MapStruct gegenereerde {@code map*}-methodes.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public abstract class PartijMapper {

    private static final Duration LAST_USED_TOUCH_THRESHOLD = Duration.ofHours(24);

    public PartijResponse toResponse(Partij partij) {
        return toResponse(partij, partij.getContactgegevens(), partij.getVoorkeuren());
    }

    @Mapping(target = "partijId", source = "partij.id")
    @Mapping(target = "identificaties", source = "partij.identificaties")
    @Mapping(target = "contactgegevens", source = "contactgegevens", qualifiedByName = "contactgegevenMetGebruik")
    @Mapping(target = "voorkeuren", source = "voorkeuren", qualifiedByName = "voorkeurMetGebruik")
    public abstract PartijResponse toResponse(
            Partij partij,
            List<Contactgegeven> contactgegevens,
            List<Voorkeur> voorkeuren);

    abstract IdentificatieResponse toIdentificatieResponse(Identificatie identificatie);

    @Named("contactgegevenMetGebruik")
    public ContactgegevenResponse toContactgegevensResponse(Contactgegeven cg) {
        Instant clearedAt = registreerGebruik(cg);
        ContactgegevenResponse cr = mapContactgegeven(cg);

        if (clearedAt != null) {
            cr.setLastUpdated(clearedAt);
            cr.setTeVerwijderenOp(null);
        }

        return cr;
    }

    @Named("voorkeurMetGebruik")
    public VoorkeurResponse toVoorkeurResponse(Voorkeur voorkeur) {
        Instant clearedAt = registreerGebruik(voorkeur);
        VoorkeurResponse vr = mapVoorkeur(voorkeur);

        if (clearedAt != null) {
            vr.setLastUpdated(clearedAt);
            vr.setTeVerwijderenOp(null);
        }

        return vr;
    }

    abstract ContactgegevenResponse mapContactgegeven(Contactgegeven cg);

    abstract VoorkeurResponse mapVoorkeur(Voorkeur voorkeur);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeContactgegeven scope);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeVoorkeur scope);

    /**
     * Registreert dat een contactgegeven is gebruikt. Bij een lang ongebruikt gegeven wordt
     * {@code lastUsedAt} bijgewerkt en een automatisch gezette verwijderdatum teruggedraaid.
     *
     * @return het moment waarop een automatische verwijderdatum is teruggedraaid, of {@code null}
     *         wanneer er niets is teruggedraaid.
     */
    private static Instant registreerGebruik(Contactgegeven cg) {
        if (!isStale(cg.getLastUsedAt())) {
            return null;
        }

        if (cg.isTeVerwijderenOpAutomatisch()) {
            Instant clearedAt = Instant.now();
            Contactgegeven.update(
                    "lastUsedAt = ?1, teVerwijderenOp = null, teVerwijderenOpAutomatisch = false, lastUpdated = ?1 where id = ?2",
                    clearedAt, cg.id);

            return clearedAt;
        }

        Contactgegeven.update("lastUsedAt = ?1 where id = ?2", Instant.now(), cg.id);

        return null;
    }

    /**
     * Registreert dat een voorkeur is gebruikt. Zie {@link #registreerGebruik(Contactgegeven)}.
     */
    private static Instant registreerGebruik(Voorkeur voorkeur) {
        if (!isStale(voorkeur.getLastUsedAt())) {
            return null;
        }

        if (voorkeur.isTeVerwijderenOpAutomatisch()) {
            Instant clearedAt = Instant.now();
            Voorkeur.update(
                    "lastUsedAt = ?1, teVerwijderenOp = null, teVerwijderenOpAutomatisch = false, lastUpdated = ?1 where id = ?2",
                    clearedAt, voorkeur.id);

            return clearedAt;
        }

        Voorkeur.update("lastUsedAt = ?1 where id = ?2", Instant.now(), voorkeur.id);

        return null;
    }

    private static boolean isStale(Instant lastUsedAt) {
        return lastUsedAt == null
                || lastUsedAt.plus(LAST_USED_TOUCH_THRESHOLD).isBefore(Instant.now());
    }
}
