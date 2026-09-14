package nl.rijksoverheid.moz.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IdentificatieNummerValidator implements ConstraintValidator<ValidIdentificatieNummer, HeeftIdentificatie> {

    @Override
    public boolean isValid(HeeftIdentificatie request, ConstraintValidatorContext context) {
        if (request == null || request.getIdentificatieNummer() == null || request.getIdentificatieType() == null) {
            return true; // Let @NotNull handle null checks
        }

        return switch (request.getIdentificatieType()) {
            case BSN -> isValidBSN(request.getIdentificatieNummer());
            case KVK -> isValidKVK(request.getIdentificatieNummer());
            case RSIN -> isValidRSIN(request.getIdentificatieNummer());
        };
    }

    private boolean isValidBSN(String bsn) {
        if (bsn.length() > 9 || !bsn.matches("\\d+")) {
            return false;
        }

        return passesElfProef(bsn);
    }

    private boolean isValidRSIN(String rsin) {
        if (rsin.length() > 9 || !rsin.matches("\\d+")) {
            return false;
        }

        return passesElfProef(rsin);
    }

    private boolean isValidKVK(String kvk) {
        // KVK must be numeric
        if (!kvk.matches("\\d+")) {
            return false;
        }

        // KVK has 8 digits main number, optionally followed by 3 digits subdossier number
        // Total length: 8-11 digits (can start with 0)
        int length = kvk.length();
        return length == 8 || length == 11;
    }

    private boolean passesElfProef(String number) {
        // Pad with leading zeros if needed
        String paddedNumber = String.format("%" + 9 + "s", number).replace(' ', '0');

        // 11-check algorithm
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = Character.getNumericValue(paddedNumber.charAt(i));
            int multiplier = 9 - i;

            if (multiplier == 1) {
                multiplier = -1;
            }

            sum += digit * multiplier;
        }

        return sum > 0 && sum % 11 == 0;
    }
}
