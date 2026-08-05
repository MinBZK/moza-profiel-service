package nl.rijksoverheid.moz.mapper;

import nl.rijksoverheid.moz.dto.response.ContactgegevenResponse;
import nl.rijksoverheid.moz.dto.response.IdentificatieResponse;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.dto.response.ScopeResponse;
import nl.rijksoverheid.moz.dto.response.VoorkeurResponse;
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
 * gebruikt wordt {@code lastUsedAt} bijgewerkt. Die business-logica is bewust handgeschreven
 * en delegeert het kopiëren naar de door MapStruct gegenereerde {@code map*}-methodes.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public abstract class PartijMapper {

    private static final Duration LAST_USED_TOUCH_THRESHOLD = Duration.ofHours(24);

    public PartijResponse toResponse(Partij partij) {
        java.util.List<Contactgegeven> contactgegevens = Contactgegeven.find("partij = ?1 AND verwijderdOp IS NULL", partij).list();
        java.util.List<Voorkeur> voorkeuren = Voorkeur.find("partij = ?1 AND verwijderdOp IS NULL", partij).list();

        return toResponse(partij, contactgegevens, voorkeuren);
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
        if (isStale(cg.getLastUsedAt())) {
            Contactgegeven.update("lastUsedAt = ?1 where id = ?2", Instant.now(), cg.id);
        }

        return mapContactgegeven(cg);
    }

    @Named("voorkeurMetGebruik")
    public VoorkeurResponse toVoorkeurResponse(Voorkeur voorkeur) {
        if (isStale(voorkeur.getLastUsedAt())) {
            Voorkeur.update("lastUsedAt = ?1 where id = ?2", Instant.now(), voorkeur.id);
        }

        return mapVoorkeur(voorkeur);
    }

    abstract ContactgegevenResponse mapContactgegeven(Contactgegeven cg);

    abstract VoorkeurResponse mapVoorkeur(Voorkeur voorkeur);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeContactgegeven scope);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeVoorkeur scope);

    private static boolean isStale(Instant lastUsedAt) {
        return lastUsedAt == null
                || lastUsedAt.plus(LAST_USED_TOUCH_THRESHOLD).isBefore(Instant.now());
    }
}
