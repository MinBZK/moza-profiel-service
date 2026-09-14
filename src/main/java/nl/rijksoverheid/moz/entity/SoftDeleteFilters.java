package nl.rijksoverheid.moz.entity;

// Losse klasse i.p.v. de constante op VerwijderbareEntiteit: alleen ACTIEF wordt buiten dit
// package gebruikt (PartijService, RetentieScheduler), en dat mag niet de reden zijn waarom de
// hele basisklasse public moet zijn.
public final class SoftDeleteFilters {

    // Actief = nog niet soft-deleted. Eén constante zodat elke finder dezelfde filter gebruikt.
    public static final String ACTIEF = "verwijderdOp IS NULL";

    private SoftDeleteFilters() {
    }
}
