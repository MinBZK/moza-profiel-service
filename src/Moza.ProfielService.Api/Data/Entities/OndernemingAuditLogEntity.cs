using System.ComponentModel.DataAnnotations.Schema;

namespace Moza.ProfielService.Api.Data.Entities;

[Table("ondernemingenAuditLog")]
public class OndernemingAuditLogEntity
{
    public int? Id { get; set; }
    public required string KvkNummer { get; set; }
    public required string Action { get; set; } = string.Empty;
    public string? Field { get; set; }
    public string? OldValue { get; set; }
    public string? NewValue { get; set; }
    public required string PerformedBy { get; set; } = string.Empty;
    public DateTime PerformedAt { get; set; }
}