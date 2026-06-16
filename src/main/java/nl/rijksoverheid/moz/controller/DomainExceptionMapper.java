package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.exception.AuthorizationException;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.TechnicalException;
import nl.rijksoverheid.moz.helper.Problems;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class DomainExceptionMapper {

    private static final Logger LOG = Logger.getLogger(DomainExceptionMapper.class);

    @ServerExceptionMapper
    public Response mapBusinessException(BusinessException e) {
        Response.Status status = switch (e.getKind()) {
            case NOT_FOUND -> Response.Status.NOT_FOUND;
            case CONFLICT -> Response.Status.CONFLICT;
            case BAD_REQUEST -> Response.Status.BAD_REQUEST;
        };
        LOG.warn("BusinessException: " + e.getMessage());

        return Problems.problemResponse(status, e.getTitle(), e.getMessage());
    }

    @ServerExceptionMapper
    public Response mapTechnicalException(TechnicalException e) {
        LOG.error("TechnicalException: " + e.getMessage(), e);

        return Problems.problemResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getTitle(), e.getMessage());
    }

    @ServerExceptionMapper
    public Response mapAuthorizationException(AuthorizationException e) {
        LOG.warn("AuthorizationException: " + e.getMessage());

        return Problems.problemResponse(Response.Status.FORBIDDEN, e.getTitle(), e.getMessage());
    }

    @ServerExceptionMapper
    public Response mapUnhandledException(Exception e) {
        LOG.error("Onverwachte fout opgetreden", e);

        return Problems.problemResponse(
                Response.Status.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Er is een onverwachte fout opgetreden");
    }
}
