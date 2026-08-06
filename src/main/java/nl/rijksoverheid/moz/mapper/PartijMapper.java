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

import java.util.List;

/**
 * Mapt {@link Partij}-entiteiten naar hun response-DTO's. Zuiver veld-voor-veld kopiëren,
 * gegenereerd door MapStruct. Geen databasetoegang: het laden van de contactgegevens/voorkeuren
 * is aan de aanroeper, zie {@link nl.rijksoverheid.moz.services.PartijService}.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public abstract class PartijMapper {

    @Mapping(target = "partijId", source = "partij.id")
    @Mapping(target = "identificaties", source = "partij.identificaties")
    // Bron expliciet aan de parameter gebonden, niet aan partij.contactgegevens/partij.voorkeuren.
    @Mapping(target = "contactgegevens", source = "contactgegevens")
    @Mapping(target = "voorkeuren", source = "voorkeuren")
    public abstract PartijResponse toResponse(
            Partij partij,
            List<Contactgegeven> contactgegevens,
            List<Voorkeur> voorkeuren);

    abstract IdentificatieResponse toIdentificatieResponse(Identificatie identificatie);

    public abstract ContactgegevenResponse mapContactgegeven(Contactgegeven cg);

    public abstract VoorkeurResponse mapVoorkeur(Voorkeur voorkeur);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeContactgegeven scope);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeVoorkeur scope);
}
