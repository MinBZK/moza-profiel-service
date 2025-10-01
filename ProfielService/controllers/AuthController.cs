using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;

using Microsoft.AspNetCore.Mvc;
using Microsoft.IdentityModel.Tokens;

using ProfielService.Responses;

namespace ProfielService.Controllers;

[ApiController]
[Route("auth")]
public class AuthController : ControllerBase
{
    private const string SecretKey = "this_is_a_fake_secret_key_for_testing_purpose_onlyr0iwkd9ewuj0q9ikwef09we1qeui9";

    [HttpGet("generate-token")]
    public IActionResult GenerateToken()
    {
        var key = Encoding.ASCII.GetBytes(SecretKey);

        var claims = new[]
        {
            new Claim(ClaimTypes.Name, "Test User"),
            new Claim(ClaimTypes.Role, "Admin")
        };

        var token = new JwtSecurityToken(
            claims: claims,
            expires: DateTime.UtcNow.AddHours(1),
            signingCredentials: new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256)
        );

        var tokenString = new JwtSecurityTokenHandler().WriteToken(token);

        return this.Ok(new TokenResponse { Token = tokenString });
    }
}
