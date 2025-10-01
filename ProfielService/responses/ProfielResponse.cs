using System.Text.Json.Serialization;

using ProfielService.Models;
using ProfielService.services.clients;

namespace ProfielService.Responses;

public class AuditLogResponse
{
    /// <summary>
    /// AuditLogs
    /// </summary>
    [JsonPropertyName(nameof(OndernemingAuditLog))]
    public required List<OndernemingAuditLog>? ondernemingAuditLogs { get; set; }
}
