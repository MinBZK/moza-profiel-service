using System.Text.Json.Serialization;

namespace Moza.ProfielService.Api.Models.Responses;

public class AuditLogResponse
{
    /// <summary>
    /// AuditLogs
    /// </summary>
    [JsonPropertyName(nameof(OndernemingAuditLog))]
    public List<OndernemingAuditLog>? ondernemingAuditLogs { get; set; }
}
