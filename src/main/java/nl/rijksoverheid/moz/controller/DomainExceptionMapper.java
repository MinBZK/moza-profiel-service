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
        return Problems.problemResponse(status, e.getTitle(), e.getDetail());
    }

    @ServerExceptionMapper
    public Response mapTechnicalException(TechnicalException e) {
        LOG.error("Technische fout: " + e.getDetail(), e);
        return Problems.problemResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getTitle(), e.getDetail());
    }

    @ServerExceptionMapper
    public Response mapAuthorizationException(AuthorizationException e) {
        return Problems.problemResponse(Response.Status.FORBIDDEN, e.getTitle(), e.getDetail());
    }

    @ServerExceptionMapper
    public Response mapUnhandledException(Exception e) {
        LOG.error("Onverwachte fout opgetreden", e);
        return Problems.problemResponse(
                Response.Status.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Er is een onverwachte fout opgetreden");
    }
}
