# CLAUDE.md

Richtlijnen voor het werken aan de moza-profiel-service.

## Codestijl

### Witregels rond `if`-statements

Gebruik witregels rondom `if`-statements voor de leesbaarheid: een lege regel
vóór het `if`-blok, ná het `if`-blok, en vóór een afsluitende `return`. Een `if`
dat het eerste statement van een methode of blok is heeft geen witregel ervóór
nodig.

```java
// Niet
Instant clearedAt = registreerGebruik(cg);
ContactgegevenResponse cr = mapContactgegeven(cg);
if (clearedAt != null) {
    cr.lastUpdated = clearedAt;
    cr.teVerwijderenOp = null;
}
return cr;

// Wel
Instant clearedAt = registreerGebruik(cg);
ContactgegevenResponse cr = mapContactgegeven(cg);

if (clearedAt != null) {
    cr.lastUpdated = clearedAt;
    cr.teVerwijderenOp = null;
}

return cr;
```
