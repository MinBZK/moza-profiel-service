package nl.rijksoverheid.moz.mapper;

import nl.rijksoverheid.moz.dto.response.DienstResponse;
import nl.rijksoverheid.moz.dto.response.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * Mapt {@link Dienstverlener}- en {@link Dienst}-entiteiten naar hun response-DTO's.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface DienstMapper {

    DienstResponse toDienstResponse(Dienst dienst);

    @Mapping(target = "naam", source = "dienstverlener.naam")
    @Mapping(target = "beschrijving", source = "dienstverlener.beschrijving")
    @Mapping(target = "diensten", source = "diensten")
    DienstverlenerResponse toDienstverlenerResponse(Dienstverlener dienstverlener, List<Dienst> diensten);
}
