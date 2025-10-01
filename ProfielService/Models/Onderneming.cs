namespace ProfielService.Models;

public class Onderneming(string kvkNummer, string? email = null, ICollection<EmailModel>? emails = null)
{
    public string KvkNummer { get; set; } = kvkNummer;
    public string? Email { get; set; } = email;
    public bool EmailVerified { get; set; } = false;
    public ICollection<EmailModel>? Emails { get; set; } = emails;
}
