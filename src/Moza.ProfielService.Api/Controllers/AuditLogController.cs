using Microsoft.AspNetCore.Mvc;

using Moza.ProfielService.Api.Models.Responses;
using Moza.ProfielService.Api.Services;

namespace Moza.ProfielService.Api.Controllers;

[ApiController]
[Route("auditlogs")]
public class AuditLogController(AuditLogService auditLogService) : ControllerBase
{
    [HttpGet("{kvkNummer}")]
    public async Task<ActionResult<AuditLogResponse>> GetOndernemingProfiel(string kvkNummer, bool ascending)
    {
        var auditLogs = await auditLogService.GetAuditLogs(kvkNummer, ascending);
        var response = new AuditLogResponse { ondernemingAuditLogs = auditLogs };
        return this.Ok(response);
    }
}