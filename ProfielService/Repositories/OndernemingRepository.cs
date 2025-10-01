using AutoMapper;

using Microsoft.EntityFrameworkCore;

using ProfielService.Data;
using ProfielService.Data.Entities;
using ProfielService.Models;

namespace ProfielService.Repositories;

public class OndernemingRepository(ProfielDbContext context, IMapper mapper, IHttpContextAccessor httpContextAccessor)
{
    public async Task<Onderneming?> GetByKvkNummerAsync(string kvkNummer)
    {
        var entity = await context.ondernemingen
            //.Include(o => o.Emails) // Niet vergeten te includen voor V2
            .FirstOrDefaultAsync(o => o.KvkNummer == kvkNummer);

        if (entity == null)
            return null;

        await context.SaveChangesAsync();

        return mapper.Map<Onderneming>(entity);
    }



    public async Task<Onderneming> Update(Onderneming onderneming)
    {
        var existingOnderneming = await context.ondernemingen
            .FirstOrDefaultAsync(o => o.KvkNummer == onderneming.KvkNummer);

        if (existingOnderneming != null)
        {
            existingOnderneming.Email = onderneming.Email;
            existingOnderneming.EmailVerified = onderneming.EmailVerified;
            existingOnderneming.Emails = mapper.Map<List<EmailEntity>>(new List<EmailModel>(onderneming.Emails));
        }
        else
        {
            var ondernemingEntity = mapper.Map<OndernemingEntity>(onderneming);
            context.ondernemingen.Add(ondernemingEntity);
        }

        await context.SaveChangesAsync();

        return onderneming;
    }

    public async Task Delete(string kvkNummer)
    {
        var existingOnderneming = await context.ondernemingen
            //.Include(o => o.Emails) // Niet vergeten te includen voor V2
            .FirstOrDefaultAsync(o => o.KvkNummer == kvkNummer);

        if (existingOnderneming != null && !existingOnderneming.IsDeleted)
        {
            existingOnderneming.IsDeleted = true;
            existingOnderneming.DeletedAt = DateTime.UtcNow;

            await context.SaveChangesAsync();
        }
    }

    public async Task<Onderneming?> GetByKvkNummerAtAsync(string kvkNummer, DateTime timestamp)
    {
        var entity = await context.ondernemingen.FirstOrDefaultAsync(o => o.KvkNummer == kvkNummer);
        if (entity == null)
            return null;


        var reconstructed = entity == null
            ? new Onderneming(kvkNummer)
            : mapper.Map<Onderneming>(entity);

        var utcTimestamp = timestamp.ToUniversalTime();

        var fieldLogs = await context.ondernemingenAuditLog
            .Where(l => l.KvkNummer == kvkNummer && l.PerformedAt > utcTimestamp)
            .OrderByDescending(l => l.PerformedAt)
            .ToListAsync();

        foreach (var log in fieldLogs)
        {
            if (string.IsNullOrEmpty(log.Field))
                continue;

            switch (log.Field)
            {
                case "Email":
                    reconstructed.Email = log.OldValue;
                    break;
            }
        }

        return reconstructed;
    }
}
