using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

using Moza.ProfielService.Api.External.Clients;
using Moza.ProfielService.Api.Models.Responses;
using Moza.ProfielService.Api.Services;

namespace Moza.ProfielService.Api.Controllers;

[ApiController]
[Authorize]
[Route("ondernemingen")]
public class ProfielController(
    EmailVerificatieClient emailVerificatieClient,
    KvkProfielClientService kvkProfielClientService,
    OndernemingService ondernemingService,
    IConfiguration config) : ControllerBase
{
    private const string EmailVerificatieReference = "Test-Moza-Profielservice";

    [HttpGet("{kvkNummer}")]
    [HttpGet("{kvkNummer}/{timestamp?}")]
    public async Task<ActionResult<ProfielResponse>> GetOndernemingProfiel(
        string kvkNummer,
        DateTime? timestamp = null)
    {
        var effectiveTimestamp = timestamp ?? DateTime.UtcNow;
        var onderneming = await ondernemingService.GetOndernemingAt(kvkNummer, effectiveTimestamp);
        var kvkProfiel = await kvkProfielClientService.GetBasisprofielByKvkNummerAsync(kvkNummer);
        var response = new ProfielResponse { Onderneming = onderneming, KvkProfiel = kvkProfiel, UwvProfiel = null };
        return this.Ok(response);
    }

    [HttpGet("EmailBekend/{kvkNummer}")]
    public async Task<ActionResult<bool>> GetOndernemingHasEmail(string kvkNummer)
    {
        var onderneming = await ondernemingService.GetOnderneming(kvkNummer);
        return this.Ok(onderneming.Email != null);
    }

    [HttpPut("{kvkNummer}")]
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
        if (response is not { Success: true })
        {
            return this.StatusCode(500);
        }

        var result = await kvkProfielClientService.GetBasisprofielByKvkNummerAsync(kvkNummer);
        var profielResponse = new ProfielResponse { Onderneming = updatedEntity, KvkProfiel = result, UwvProfiel = null };
        return this.Ok(profielResponse);
    }

    [HttpPost("{kvkNummer}/verify")]
    public async Task<ActionResult> VerifyEmail(string kvkNummer, string code)
    {
        var onderneming = await ondernemingService.GetOnderneming(kvkNummer);
        var body = new { code, reference = EmailVerificatieReference, email = onderneming.Email };
        Response2 response;

        try
        {
            response = await emailVerificatieClient.VerifyAsync(body);
        }
        catch (ApiException e)
        {
            return e.StatusCode == 404 ? this.NotFound() : this.BadRequest();
        }

        if (!response.Verified) return this.Ok("Failed to verify email");

        await ondernemingService.VerifyEmail(kvkNummer);
        return this.Ok();
    }

    [HttpDelete("{kvkNummer}")]
    public async Task<ActionResult<ProfielResponse>> RemoveEmail(string kvkNummer)
    {
        await ondernemingService.DeleteEmailToOndernemingAsync(kvkNummer);
        return this.Ok();
    }
}