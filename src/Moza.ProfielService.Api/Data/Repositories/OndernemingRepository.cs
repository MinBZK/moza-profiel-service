using AutoMapper;

using Microsoft.EntityFrameworkCore;

using Moza.ProfielService.Api.Data.Entities;
using Moza.ProfielService.Api.Models;

namespace Moza.ProfielService.Api.Data.Repositories;

public class OndernemingRepository(ProfielDbContext context, IMapper mapper)
{
    public async Task<Onderneming?> GetByKvkNummerAsync(string kvkNummer)
    {
        var entity = await context.Ondernemingen
            //.Include(o => o.Emails) // Niet vergeten te includen voor V2
            .FirstOrDefaultAsync(o => o.KvkNummer == kvkNummer);

        if (entity == null) return null;

        await context.SaveChangesAsync();
        return mapper.Map<Onderneming>(entity);
    }

    public async Task<Onderneming> Update(Onderneming onderneming)
    {
        ArgumentNullException.ThrowIfNull(onderneming);

        var existingOnderneming = await context.Ondernemingen
            .FirstOrDefaultAsync(o => o.KvkNummer == onderneming.KvkNummer);

        if (existingOnderneming != null)
        {
            existingOnderneming.Email = onderneming.Email!;
            existingOnderneming.EmailVerified = onderneming.EmailVerified;
            existingOnderneming.Emails = mapper.Map<List<EmailEntity>>(new List<OndernemingEmail>(onderneming.Emails!));
        }
        else
        {
            var ondernemingEntity = mapper.Map<OndernemingEntity>(onderneming);
            context.Ondernemingen.Add(ondernemingEntity);
        }

        await context.SaveChangesAsync();
        return onderneming;
    }

    public async Task Delete(string kvkNummer)
    {
        var existingOnderneming = await context.Ondernemingen
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
        var entity = await context.Ondernemingen.FirstOrDefaultAsync(o => o.KvkNummer == kvkNummer);
        if (entity == null) return null;

        var reconstructed = entity == null
            ? new Onderneming(kvkNummer)
            : mapper.Map<Onderneming>(entity);

        var utcTimestamp = timestamp.ToUniversalTime();

        var fieldLogs = await context.OndernemingenAuditLog
            .Where(l => l.KvkNummer == kvkNummer && l.PerformedAt > utcTimestamp)
            .OrderByDescending(l => l.PerformedAt)
            .ToListAsync();

        foreach (var log in fieldLogs.Where(log => !string.IsNullOrEmpty(log.Field)))
            reconstructed.Email = log.Field switch
            {
                "Email" => log.OldValue,
                _ => reconstructed.Email
            };

        return reconstructed;
    }
}
