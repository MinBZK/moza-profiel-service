namespace Moza.ProfielService.Api.Models;

public class Onderneming(string kvkNummer, string? email = null, ICollection<OndernemingEmail>? emails = null)
{
    public string KvkNummer { get; set; } = kvkNummer;
    public string? Email { get; set; } = email;
    public bool EmailVerified { get; set; }
    public ICollection<OndernemingEmail>? Emails { get; set; } = emails;
}