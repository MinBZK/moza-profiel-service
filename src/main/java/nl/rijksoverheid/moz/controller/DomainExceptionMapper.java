package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.TechnicalException;
import nl.rijksoverheid.moz.helper.Problems;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class DomainExceptionMapper {

    private static final Logger LOG = Logger.getLogger(DomainExceptionMapper.class);

    @ServerExceptionMapper
    public Response mapBusinessException(BusinessException e, UriInfo uriInfo) {
        Response.Status status = switch (e.getKind()) {
            case NOT_FOUND -> Response.Status.NOT_FOUND;
            case CONFLICT -> Response.Status.CONFLICT;
            case BAD_REQUEST -> Response.Status.BAD_REQUEST;
        };
        LOG.warnf("BusinessException %s (%s): %s", e.getKind(), e.getTitle(), e.getMessage());
        // Stacktrace alleen op DEBUG: dit zijn clientfouten, die horen de log niet te vullen.
        LOG.debug("Werpplek van de BusinessException", e);

        return Problems.problemResponse(status, e.getTitle(), e.getMessage(), uriInfo);
    }

    @ServerExceptionMapper
    public Response mapTechnicalException(TechnicalException e, UriInfo uriInfo) {
        LOG.error("TechnicalException: " + e.getMessage(), e);

        return Problems.problemResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getTitle(),
                e.getMessage(), uriInfo);
    }

    @ServerExceptionMapper
    public Response mapUnhandledException(Exception e, UriInfo uriInfo) {
        LOG.error("Onverwachte fout opgetreden", e);

        return Problems.problemResponse(
                Response.Status.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Er is een onverwachte fout opgetreden",
                uriInfo);
    }
}
