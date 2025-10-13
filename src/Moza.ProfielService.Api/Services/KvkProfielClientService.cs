using Microsoft.Extensions.Caching.Memory;

using Moza.ProfielService.Api.External.Clients;

namespace Moza.ProfielService.Api.Services;

public class KvkProfielClientService(KvkProfielClient kvkProfielClient, IMemoryCache cache)
{
    private readonly TimeSpan cacheDuration = TimeSpan.FromDays(1);

    public async Task<Basisprofiel> GetBasisprofielByKvkNummerAsync(string kvkNummer)
    {
        if (cache.TryGetValue(kvkNummer, out Basisprofiel? cachedProfile) && cachedProfile != null) return cachedProfile;

        var kvkProfiel = await kvkProfielClient.GetBasisprofielByKvkNummerAsync(kvkNummer, false);

        if (kvkProfiel.KvkNummer != null) // Dont save if you get a null response, can be done cleaner
        {
            cache.Set(kvkNummer, kvkProfiel, this.cacheDuration);
        }

        return kvkProfiel;
    }
}