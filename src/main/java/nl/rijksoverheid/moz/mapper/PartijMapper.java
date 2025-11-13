package nl.rijksoverheid.moz.mapper;

import nl.rijksoverheid.moz.dto.response.*;
import nl.rijksoverheid.moz.entity.Contactvoorkeur;
import nl.rijksoverheid.moz.entity.Partij;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta", uses = ContactvoorkeurMapper.class)
public interface PartijMapper {

    PartijResponse toPartijResponse(Partij partij);

    @Mapping(source = "eigenaar", target = "contactvoorkeuren")
    EigenPartijResponse toEigenaarPartijResponse(Partij partij);

    @Mapping(source = "betreffende", target = "contactvoorkeuren")
    BetreffendePartijResponse toBetreffendePartijResponse(Partij partij);

}

