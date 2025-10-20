using System.Text.Json.Serialization;

using Moza.ProfielService.Api.External.Clients;

namespace Moza.ProfielService.Api.Models.Responses;

public class ProfielResponse
{
    /// <summary>
    /// Email adres van bedrijf
    /// </summary>
    [JsonPropertyName(nameof(Onderneming))]
    public required Onderneming? Onderneming { get; set; }

    /// <summary>
    /// Het profiel van een bedrijf vanuit de kvk
    /// </summary>
    [JsonPropertyName(nameof(KvkProfiel))]
    public required Basisprofiel KvkProfiel { get; set; }

    /// <summary>
    /// Het profiel van een bedrijf vanuit het uwv
    /// </summary>
    [JsonPropertyName(nameof(UwvProfiel))]
    public object? UwvProfiel { get; set; }
}
