package nl.rijksoverheid.moz.mapper;

import nl.rijksoverheid.moz.dto.response.BetreffendeContactvoorkeurResponse;
import nl.rijksoverheid.moz.dto.response.EigenaarContactvoorkeurResponse;
import nl.rijksoverheid.moz.entity.Contactvoorkeur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface ContactvoorkeurMapper {
    @Mapping(source = "betreffendePartij", target = "betreffendePartij")
    @Mapping(target = "isGeverifieerd", expression = "java(contactvoorkeur.getGeverifieerdAt() != null)")
    EigenaarContactvoorkeurResponse toEigenaarContactvoorkeurResponse(Contactvoorkeur contactvoorkeur);

    @Mapping(source = "eigenaarPartij", target = "eigenaarPartij")
    @Mapping(target = "isGeverifieerd", expression = "java(contactvoorkeur.getGeverifieerdAt() != null)")
    BetreffendeContactvoorkeurResponse toBetreffendeContactvoorkeurResponse(Contactvoorkeur contactvoorkeur);
}
