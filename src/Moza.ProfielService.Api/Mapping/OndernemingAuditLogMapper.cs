using AutoMapper;

using Moza.ProfielService.Api.Data.Entities;
using Moza.ProfielService.Api.Models;

namespace Moza.ProfielService.Api.Mapping;

public class OndernemingAuditLogMapper : Profile
{
    public OndernemingAuditLogMapper()
    {
        this.CreateMap<OndernemingAuditLogEntity, OndernemingAuditLog>().ReverseMap();
    }
}
