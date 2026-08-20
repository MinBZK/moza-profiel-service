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
 * gegenereerd door MapStruct. Geen databasetoegang: het laden van de contactgegevens/voorkeuren,
 * en het bijwerken van lastUsedAt bij een stale read ("touch on read"), is aan de aanroeper, zie
 * {@link nl.rijksoverheid.moz.services.PartijService#touchIfStale}.
 *
 * <p>De {@code remove*Item}-doelen worden expliciet genegeerd. De generator zet bij elke
 * lijst-property een {@code addXItem} en een {@code removeXItem} neer die de klasse zelf
 * teruggeven; MapStruct leest zo'n methode als fluent setter en houdt er dus een
 * doel-property {@code removeXItem} aan over die nergens vandaan te vullen is. Bij
 * {@code addXItem} gebeurt dat niet, omdat de {@code add}-prefix hem als adder
 * classificeert en daarmee diskwalificeert als fluent setter — gebruikt wordt hij
 * evenmin, want de standaard {@code CollectionMappingStrategy} is {@code ACCESSOR_ONLY}
 * en vult de lijst via de gewone setter. Vandaar dat alleen de remove-kant overblijft.
 *
 * <p>{@code unmappedTargetPolicy} staat op {@code ERROR}: zonder die instelling zouden de
 * ignores alleen de waarschuwingen opruimen, en zou een échte niet-gemapte property nog
 * steeds een regel ruis tussen de andere buildwaarschuwingen zijn.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class PartijMapper {

    // public: aangeroepen vanuit andere packages (PartijService, ProfielController). De
    // toIdentificatieResponse/toScopeResponse-submappings hieronder zijn package-private: die
    // worden alleen door de MapStruct-gegenereerde impl in dit package zelf aangeroepen.
    @Mapping(target = "partijId", source = "partij.id")
    @Mapping(target = "identificaties", source = "partij.identificaties")
    // Bron expliciet aan de parameter gebonden, niet aan partij.contactgegevens/partij.voorkeuren.
    @Mapping(target = "contactgegevens", source = "contactgegevens")
    @Mapping(target = "voorkeuren", source = "voorkeuren")
    @Mapping(target = "removeIdentificatiesItem", ignore = true)
    @Mapping(target = "removeContactgegevensItem", ignore = true)
    @Mapping(target = "removeVoorkeurenItem", ignore = true)
    public abstract PartijResponse toResponse(
            Partij partij,
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
