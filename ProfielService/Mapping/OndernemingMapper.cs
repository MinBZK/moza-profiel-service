using AutoMapper;

using ProfielService.Data.Entities;
using ProfielService.Models;

namespace ProfielService.Mapping;

public class OndernemingMapper : Profile
{
    public OndernemingMapper()
    {
        this.CreateMap<OndernemingEntity, Onderneming>().ReverseMap();
    }
}
