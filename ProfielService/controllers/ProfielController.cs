using Microsoft.AspNetCore.Mvc;

using ProfielService.Data.Migrations;

using ProfielService.Responses;
using ProfielService.services.clients;
using ProfielService.Services;

namespace ProfielService.controllers;

[ApiController]
[Route("ondernemingen")]
public class ProfielController(EmailVerificatieClient emailVerificatieClient, KvkProfielClientService kvkProfielClientService, OndernemingService ondernemingService, IConfiguration config) : ControllerBase
{
    private const string EmailVerificatieReference = "Test-Moza-Profielservice";

    [HttpGet("{kvkNummer}")]
    [HttpGet("{kvkNummer}/{timestamp?}")]
    //[Authorize] // Uitcommenten voor auth
    public async Task<ActionResult<ProfielResponse>> GetOndernemingProfiel(
        string kvkNummer,
        DateTime? timestamp = null)
    {
        var effectiveTimestamp = timestamp ?? DateTime.UtcNow;

        var onderneming = await ondernemingService.GetOndernemingAt(kvkNummer, effectiveTimestamp);
        var kvkProfiel = await kvkProfielClientService.GetBasisprofielByKvkNummerAsync(kvkNummer);

        var profielResponse = new ProfielResponse
        {
            Onderneming = onderneming,
            KvkProfiel = kvkProfiel,
            UwvProfiel = null
        };

        return Ok(profielResponse);
    }


    [HttpGet("EmailBekend/{kvkNummer}")]
    //[Authorize] // Uitcommenten voor auth
    public async Task<ActionResult<bool>> GetOndernemingHasEmail(string kvkNummer)
    {
        var onderneming = await ondernemingService.GetOnderneming(kvkNummer);

        return this.Ok(onderneming.Email != null);
    }

    [HttpPut("{kvkNummer}")]
    //[Authorize] // Uitcommenten voor auth
    public async Task<ActionResult<ProfielResponse>> AddEmail(string kvkNummer, string email)
    {
        var updatedEntity = await ondernemingService.SaveEmailToOndernemingAsync(kvkNummer, email);

        var body = new
        {
            apiKey = config["NotifyNL:VerifyEmailApiKey"],
            reference = EmailVerificatieReference,
            email,
            templateId = config["NotifyNL:templateId"]
        };
        var response = await emailVerificatieClient.VerificationRequestsAsync(body);

        if (response == null || !response.Success)
        {
            return this.StatusCode(500);
        }


        var result = await kvkProfielClientService.GetBasisprofielByKvkNummerAsync(kvkNummer);

        var profielResponse = new ProfielResponse
        {
            Onderneming = updatedEntity,
            KvkProfiel = result,
            UwvProfiel = null
        };

        return this.Ok(profielResponse);
    }

    [HttpPost("{kvkNummer}/verify")]
    //[Authorize] // Uitcommenten voor auth
    public async Task<ActionResult> VerifyEmail(string kvkNummer, string code)
    {

        var onderneming = await ondernemingService.GetOnderneming(kvkNummer);
        var body = new
        {
            code,
            reference = EmailVerificatieReference,
            email = onderneming.Email
        };
        Response2 response;
        try
        {
            response = await emailVerificatieClient.VerifyAsync(body);
        }
        catch (ApiException e)
        {
            return e.StatusCode == 404 ? this.NotFound() : this.BadRequest();
        }

        if (response.Verified)
        {
            await ondernemingService.VerifyEmail(kvkNummer);
            return this.Ok();
        }
        else
        {
            return this.Ok("Failed to verify email");
        }

    }


    [HttpDelete("{kvkNummer}")]
    //[Authorize] // Uitcommenten voor auth
    public async Task<ActionResult<ProfielResponse>> RemoveEmail(string kvkNummer)
    {
        await ondernemingService.DeleteEmailToOndernemingAsync(kvkNummer);

        return this.Ok();
    }
}
