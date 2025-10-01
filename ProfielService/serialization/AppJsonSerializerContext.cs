using System.Text.Json.Serialization;

using ProfielService.Models;
using ProfielService.Responses;

namespace ProfielService.Serialization;

[JsonSerializable(typeof(TokenResponse))]
[JsonSerializable(typeof(Onderneming[]))]
public partial class AppJsonSerializerContext : JsonSerializerContext
{
}
