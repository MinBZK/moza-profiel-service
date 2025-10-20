using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Moza.ProfielService.Api.Data.Entities;

[Table("ondernemingen")]
public class OndernemingEntity
{
    [Key]
    public int Id { get; set; }

    public string Email { get; set; } = string.Empty;
    public bool EmailVerified { get; set; } = false;
    public required string KvkNummer { get; set; }
    public ICollection<EmailEntity> Emails { get; set; } = [];
    public bool IsDeleted { get; set; } = false;
    public DateTime? DeletedAt { get; set; }
}