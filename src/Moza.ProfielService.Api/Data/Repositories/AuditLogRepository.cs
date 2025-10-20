using AutoMapper;

using Microsoft.EntityFrameworkCore;

using Moza.ProfielService.Api.Models;

namespace Moza.ProfielService.Api.Data.Repositories;

public class AuditLogRepository(ProfielDbContext context, IMapper mapper)
{
    public async Task<List<OndernemingAuditLog>?> GetByOndernemingIdAsync(string kvkNummer, bool ascending)
    {
        var query = context.OndernemingenAuditLog.Where(o => o.KvkNummer == kvkNummer);

        query = ascending
            ? query.OrderBy(o => o.PerformedAt)
            : query.OrderByDescending(o => o.PerformedAt);

        var entities = await query.ToListAsync();
        return mapper.Map<List<OndernemingAuditLog>>(entities);
    }
}
