using ProfielService.Models;
using ProfielService.Repositories;

namespace ProfielService.Services;

public class OndernemingService(OndernemingRepository repository)
{
    public async Task<Onderneming> GetOnderneming(string KvkNummer)
    {
        var onderneming = await repository.GetByKvkNummerAsync(KvkNummer);

        onderneming ??= new Onderneming(KvkNummer);
        return onderneming;
    }

    public async Task<Onderneming> SaveEmailToOndernemingAsync(string kvkNummer, string email)
    {
        var onderneming = await repository.GetByKvkNummerAsync(kvkNummer);

        if (onderneming == null)
            onderneming = new Onderneming(kvkNummer, email);
        else
        {
            onderneming.Email = email;
            onderneming.EmailVerified = false;
        }

        return await repository.Update(onderneming);
    }

    public async Task<Onderneming> GetOndernemingAt(string KvkNummer, DateTime timestamp)
    {
        var onderneming = await repository.GetByKvkNummerAtAsync(KvkNummer, timestamp);

        onderneming ??= new Onderneming(KvkNummer);
        return onderneming;
    }

    public async Task DeleteEmailToOndernemingAsync(string kvkNummer)
    {
        await repository.Delete(kvkNummer);
    }

    public async Task VerifyEmail(string kvkNummer)
    {
        var onderneming = await repository.GetByKvkNummerAsync(kvkNummer);
        onderneming.EmailVerified = true;
        await repository.Update(onderneming);
    }
}
