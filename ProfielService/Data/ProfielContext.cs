using Microsoft.EntityFrameworkCore;

using ProfielService.Data.Entities;

namespace ProfielService.Data;

public class ProfielDbContext(DbContextOptions<ProfielDbContext> options, IHttpContextAccessor httpContextAccessor) : DbContext(options)
{
    public DbSet<OndernemingEntity> ondernemingen { get; set; }
    public DbSet<OndernemingAuditLogEntity> ondernemingenAuditLog { get; set; }
    public DbSet<EmailEntity> emails { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<EmailEntity>()
            .HasKey(e => new { e.Email, e.OndernemingId }); // composite primary key

        modelBuilder.Entity<EmailEntity>()
            .HasOne(e => e.Onderneming)
            .WithMany(o => o.Emails)
            .HasForeignKey(e => e.OndernemingId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<OndernemingEntity>()
            .HasQueryFilter(o => !o.IsDeleted);
    }

    public override async Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
    {
        var user = httpContextAccessor.HttpContext?.User?.FindFirst("sub")?.Value
                   ?? httpContextAccessor.HttpContext?.User?.Identity?.Name
                   ?? "unknown";

        var now = DateTime.UtcNow;

        var addedEntries = ChangeTracker.Entries<OndernemingEntity>()
            .Where(e => e.State == EntityState.Added)
            .ToList();

        var logs = ChangeTracker.Entries<OndernemingEntity>()
            .Where(e => e.State == EntityState.Modified || e.State == EntityState.Deleted)
            .SelectMany(e =>
            {
                return e.State switch
                {
                    EntityState.Modified => e.Properties
                        .Where(p => p.IsModified)
                        .Select(p => new OndernemingAuditLogEntity
                        {
                            KvkNummer = e.Entity.KvkNummer,
                            Action = "UPDATE",
                            Field = p.Metadata.Name,
                            OldValue = p.OriginalValue?.ToString(),
                            NewValue = p.CurrentValue?.ToString(),
                            PerformedBy = user,
                            PerformedAt = now
                        }),
                    EntityState.Deleted =>
                    [
                    new OndernemingAuditLogEntity
                    {
                        KvkNummer = e.Entity.KvkNummer,
                        Action = "DELETE",
                        PerformedBy = user,
                        PerformedAt = now
                    }
                    ],
                    _ => []
                };
            })
            .ToList();

        var result = await base.SaveChangesAsync(cancellationToken);

        foreach (var e in addedEntries)
        {
            foreach (var p in e.Properties.Where(p => p.Metadata.Name == nameof(OndernemingEntity.Email)))
            {
                logs.Add(new OndernemingAuditLogEntity
                {
                    KvkNummer = e.Entity.KvkNummer,
                    Action = "CREATE",
                    Field = p.Metadata.Name,
                    OldValue = null,
                    NewValue = p.CurrentValue?.ToString(),
                    PerformedBy = user,
                    PerformedAt = now
                });
            }
        }

        if (logs.Count != 0)
        {
            ondernemingenAuditLog.AddRange(logs);
            await base.SaveChangesAsync(cancellationToken);
        }

        return result;
    }

}
