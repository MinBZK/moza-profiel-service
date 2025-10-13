using Moza.ProfielService.Api.Common.Enumerations;

namespace Moza.ProfielService.Api.Models;

public class OndernemingEmail(string email, DienstType dienstType = DienstType.Alles)
{
    public string Email { get; set; } = email;
    public DienstType DienstType { get; set; } = dienstType;
}
