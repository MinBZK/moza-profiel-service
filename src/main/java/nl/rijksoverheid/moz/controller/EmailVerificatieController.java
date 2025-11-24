
package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "EmailVerificatie", description = "Endpoints voor het verifieren van emails")
public class EmailVerificatieController {

    @POST
    @Path("/emailverificatie/{email}/{verificatiecode}")
    public Response getAfdelingenDienstverlener(@PathParam("email") String email,
                                                @PathParam("verificatiecode") String verificatiecode) {
        //Todo Zet email verified in database
        //Todo Maak connectie met NotifyNL en verifieer email.
        return Response.ok(true).build();
    }

}
