using System.ComponentModel.DataAnnotations;

namespace ProfielService.Enumerations;

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
