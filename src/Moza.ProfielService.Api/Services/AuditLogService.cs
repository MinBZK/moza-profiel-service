using Moza.ProfielService.Api.Data.Repositories;
using Moza.ProfielService.Api.Models;

namespace Moza.ProfielService.Api.Services;

public class AuditLogService(AuditLogRepository auditLogRepository)
{
    public async Task<List<OndernemingAuditLog>?> GetAuditLogs(string KvkNummer, bool ascending)
    {
        var auditLogList = await auditLogRepository.GetByOndernemingIdAsync(KvkNummer, ascending);
        return auditLogList;
    }
}
