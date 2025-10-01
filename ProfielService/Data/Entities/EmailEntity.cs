using System.ComponentModel.DataAnnotations;

using ProfielService.Enumerations;

namespace ProfielService.Data.Entities;

public class EmailEntity
{
    [Key]
    public string Email { get; set; } = string.Empty;

    public DienstType dienstType { get; set; } = DienstType.Alles;
    public int OndernemingId { get; set; }
    public OndernemingEntity Onderneming { get; set; } = null!;
}
