using System.ComponentModel.DataAnnotations;

namespace Moza.ProfielService.Api.Common.Enumerations;

public enum DienstType
{
    [Display(Name = "Alles")]
    Alles = 0,

    [Display(Name = "Financiën")]
    Financien = 1,

    [Display(Name = "Personeelszaken")]
    Personeelszaken = 2,

    [Display(Name = "Anders")]
    Anders = 3
}
