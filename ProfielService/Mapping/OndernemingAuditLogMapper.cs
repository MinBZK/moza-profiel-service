using AutoMapper;

using ProfielService.Data.Entities;
using ProfielService.Models;

namespace ProfielService.Mapping;

public class OndernemingAuditLogMapper : Profile
{
    public OndernemingAuditLogMapper()
    {
        this.CreateMap<OndernemingAuditLogEntity, OndernemingAuditLog>().ReverseMap();
    }
}
