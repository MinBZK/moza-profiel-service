using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

using Moza.ProfielService.Api.Common.Enumerations;

namespace Moza.ProfielService.Api.Data.Entities;

[Table("emails")]
public class EmailEntity
{
    [Key]
    public string Email { get; set; } = string.Empty;

    public DienstType DienstType { get; set; } = DienstType.Alles;
    public int OndernemingId { get; set; }
    public OndernemingEntity Onderneming { get; set; } = null!;
}