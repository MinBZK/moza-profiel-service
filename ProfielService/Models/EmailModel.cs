using ProfielService.Enumerations;

namespace ProfielService.Models;

public class EmailModel(string email, DienstType dienstType = DienstType.Alles)
{
    public string Email { get; set; } = email;
    public DienstType dienstType { get; set; } = dienstType;
}
