package nl.rijksoverheid.moz.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class PartijBulkRequest {

    @NotEmpty
    public List<PartijIdentificatieRequest> identificaties;
}
