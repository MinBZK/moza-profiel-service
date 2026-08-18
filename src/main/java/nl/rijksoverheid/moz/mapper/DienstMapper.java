package nl.rijksoverheid.moz.mapper;

import nl.rijksoverheid.moz.api.generated.model.DienstResponse;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapt {@link Dienstverlener}- en {@link Dienst}-entiteiten naar hun response-DTO's.
 *
 * <p>Zie {@link PartijMapper} voor waarom {@code removeDienstenItem} expliciet wordt
 * genegeerd en waarom {@code unmappedTargetPolicy} op {@code ERROR} staat.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DienstMapper {

    DienstResponse toDienstResponse(Dienst dienst);

    @Mapping(target = "naam", source = "dienstverlener.naam")
    @Mapping(target = "beschrijving", source = "dienstverlener.beschrijving")
    @Mapping(target = "diensten", source = "diensten")
    @Mapping(target = "removeDienstenItem", ignore = true)
    DienstverlenerResponse toDienstverlenerResponse(Dienstverlener dienstverlener, List<Dienst> diensten);
}
