using AutoMapper;

using Moza.ProfielService.Api.Data.Entities;
using Moza.ProfielService.Api.Models;

namespace Moza.ProfielService.Api.Mapping;

public class OndernemingMapper : Profile
{
    public OndernemingMapper()
    {
        this.CreateMap<OndernemingEntity, Onderneming>().ReverseMap();
    }
}
