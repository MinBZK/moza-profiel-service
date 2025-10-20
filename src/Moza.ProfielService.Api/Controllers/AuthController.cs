using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;

using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

using Moza.ProfielService.Api.Configuration;
using Moza.ProfielService.Api.Models.Responses;

namespace Moza.ProfielService.Api.Controllers;

[ApiController]
[Route("auth")]
public class AuthController(IOptions<AuthenticationSettings> authSettings) : ControllerBase
{
    private readonly string secretKey = authSettings.Value.SecretKey;

    [HttpGet("generate-token")]
    public IActionResult GenerateToken()
    {
        var key = Encoding.ASCII.GetBytes(this.secretKey);
        var claims = new[] { new Claim(ClaimTypes.Name, "Test User"), new Claim(ClaimTypes.Role, "Admin") };

        var token = new JwtSecurityToken(
            claims: claims,
            expires: DateTime.UtcNow.AddHours(1),
            signingCredentials: new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256)
        );

        var tokenString = new JwtSecurityTokenHandler().WriteToken(token);
        return this.Ok(new TokenResponse { Token = tokenString });
    }
}