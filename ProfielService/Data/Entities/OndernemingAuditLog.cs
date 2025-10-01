namespace ProfielService.Data.Entities;
public class OndernemingAuditLogEntity
{
    public int Id { get; set; }
    public string KvkNummer { get; set; }
    public string Action { get; set; } = string.Empty;
    public string? Field { get; set; }
    public string? OldValue { get; set; }
    public string? NewValue { get; set; }
    public string PerformedBy { get; set; } = string.Empty;
    public DateTime PerformedAt { get; set; }
}
