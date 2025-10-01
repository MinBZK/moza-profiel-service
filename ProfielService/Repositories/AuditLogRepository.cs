using AutoMapper;

using Microsoft.EntityFrameworkCore;

using ProfielService.Data;
using ProfielService.Data.Entities;
using ProfielService.Models;

namespace ProfielService.Repositories;

public class AuditLogRepository(ProfielDbContext context, IMapper mapper)
{
    public async Task<List<OndernemingAuditLog>?> GetByOndernemingIdAsync(string kvkNummer, bool ascending)
    {
        var query = context.ondernemingenAuditLog
            .Where(o => o.KvkNummer == kvkNummer);

        query = ascending
            ? query.OrderBy(o => o.PerformedAt)
            : query.OrderByDescending(o => o.PerformedAt);

        var entities = await query.ToListAsync();

        return mapper.Map<List<OndernemingAuditLog>>(entities);
    }

}
