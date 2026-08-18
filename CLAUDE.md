# CLAUDE.md

Richtlijnen voor het werken aan de moza-profiel-service.

## Commentaar

Houd commentaar compact. Eén of twee zinnen die zeggen wát er niet vanzelf
spreekt en waaróm. Geen alinea's met de afweging, de alternatieven en de
meetresultaten erbij; die horen in de commitmessage of het issue.

Schrijf alleen op wat je hebt gecontroleerd. Een verklaring van een mechanisme —
"de validator doet X", "de generator emit Y" — is een bewering over gedrag: klopt
hij niet, dan is hij schadelijker dan geen commentaar, want de volgende lezer
bouwt erop voort. Weet je het niet zeker, laat het weg of noteer het als aanname.

```java
// Niet
// anyOf en niet oneOf: een null-waarde matcht bij de contractvalidatie zowel de
// Instant-tak als de null-tak, en oneOf eist er precies één. Semantisch maakt
// het hier niets uit — de unie is hetzelfde — maar oneOf zou op elke lege
// verwijderdatum een valse fout geven.

// Wel
// anyOf: oneOf faalt hier op elke waarde omdat beide takken alles matchen.
```

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
