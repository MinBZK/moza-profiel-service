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
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapt {@link Partij}-entiteiten naar hun response-DTO's. Zuiver veld-voor-veld kopiëren,
 * gegenereerd door MapStruct. Geen databasetoegang: het laden van de identificaties/contactgegevens/
 * voorkeuren, en het bijwerken van lastUsedAt bij een stale read ("touch on read"), is aan de
 * aanroeper, zie {@code PartijService.touchIfStale}.
 *
 * <p>De {@code remove*Item}-doelen worden expliciet genegeerd: MapStruct leest die
 * generator-methodes als fluent setter en houdt er zo een doel-property aan over die nergens
 * vandaan te vullen is.
 *
 * <p>{@code unmappedTargetPolicy} staat op {@code ERROR}: zonder die instelling zouden de
 * ignores alleen de waarschuwingen opruimen, en zou een échte niet-gemapte property nog
 * steeds een regel ruis tussen de andere buildwaarschuwingen zijn.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class PartijMapper {

    // public: toResponse wordt vanuit een ander package aangeroepen (PartijService); mapContactgegeven/
    // mapVoorkeur hieronder vanuit ProfielController. De toIdentificatieResponse/toScopeResponse-
    // submappings zijn package-private: die worden alleen door de MapStruct-gegenereerde impl in
    // dit package zelf aangeroepen.
    @Mapping(target = "partijId", source = "partij.id")
    // Bron expliciet aan de parameters gebonden: Partij heeft alleen nog identificaties als
    // @OneToMany, en die rauwe collectie is ongefilterd (zie OngefilterdeFinderTest) — ze zou een
    // soft deleted rij laten herleven in de response.
    @Mapping(target = "identificaties", source = "identificaties")
    @Mapping(target = "contactgegevens", source = "contactgegevens")
    @Mapping(target = "voorkeuren", source = "voorkeuren")
    @Mapping(target = "removeIdentificatiesItem", ignore = true)
    @Mapping(target = "removeContactgegevensItem", ignore = true)
    @Mapping(target = "removeVoorkeurenItem", ignore = true)
    public abstract PartijResponse toResponse(
            Partij partij,
            List<Identificatie> identificaties,
            List<Contactgegeven> contactgegevens,
            List<Voorkeur> voorkeuren);

    abstract IdentificatieResponse toIdentificatieResponse(Identificatie identificatie);

    @Mapping(target = "removeScopesItem", ignore = true)
    public abstract ContactgegevenResponse mapContactgegeven(Contactgegeven cg);

    @Mapping(target = "removeScopesItem", ignore = true)
    public abstract VoorkeurResponse mapVoorkeur(Voorkeur voorkeur);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeContactgegeven scope);

    @Mapping(target = "dienstverlenerNaam", source = "dienstverlenerDienst.dienstverlener.naam")
    @Mapping(target = "dienstNaam", source = "dienstverlenerDienst.dienst.naam")
    abstract ScopeResponse toScopeResponse(ScopeVoorkeur scope);
}
