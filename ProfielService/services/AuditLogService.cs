using ProfielService.Models;
using ProfielService.Repositories;

namespace ProfielService.Services;

public class AuditLogService(AuditLogRepository auditLogRepository)
{
    public async Task<List<OndernemingAuditLog>> GetAuditLogs(string KvkNummer, bool ascending)
    {
        var auditLogList = await auditLogRepository.GetByOndernemingIdAsync(KvkNummer, ascending);

        return auditLogList;
    }
}
