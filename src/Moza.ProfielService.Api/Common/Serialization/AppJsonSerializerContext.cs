using System.Text.Json.Serialization;

using Moza.ProfielService.Api.Models;
using Moza.ProfielService.Api.Models.Responses;

namespace Moza.ProfielService.Api.Common.Serialization;

[JsonSerializable(typeof(TokenResponse))]
[JsonSerializable(typeof(Onderneming[]))]
public partial class AppJsonSerializerContext : JsonSerializerContext
{
}
