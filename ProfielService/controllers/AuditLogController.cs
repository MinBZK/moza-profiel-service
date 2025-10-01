using Microsoft.AspNetCore.Mvc;

using ProfielService.Responses;
using ProfielService.services.clients;
using ProfielService.Services;

namespace ProfielService.controllers;

[ApiController]
[Route("auditlogs")]
public class AuditLogController(AuditLogService auditLogService) : ControllerBase
{
    [HttpGet("{kvkNummer}")]
    //[Authorize] // Uitcommenten voor auth
    public async Task<ActionResult<AuditLogResponse>> GetOndernemingProfiel(string kvkNummer, bool ascending)
    {
        var auditLogs = await auditLogService.GetAuditLogs(kvkNummer, ascending);

        var auditLogResponse = new AuditLogResponse
        {
            ondernemingAuditLogs = auditLogs
        };

        return this.Ok(auditLogResponse);
    }
}
