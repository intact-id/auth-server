package com.intact_id.orchestration.privacy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Country codes as retention uses them: ISO 3166 alpha-2, from a code (alpha-2 or -3) or an English name. */
public final class Countries {

    public static final String ANY = "*";
    private static final Map<String, String> TO_ALPHA2 = new HashMap<>();

    static {
        for (String alpha2 : Locale.getISOCountries()) {
            Locale locale = new Locale("", alpha2);
            TO_ALPHA2.put(alpha2, alpha2);
            try {
                TO_ALPHA2.put(locale.getISO3Country(), alpha2);
            } catch (java.util.MissingResourceException ignored) {
                // a few codes have no alpha-3
            }
            TO_ALPHA2.put(locale.getDisplayCountry(Locale.ENGLISH).toUpperCase(Locale.ROOT), alpha2);
        }
    }

    private Countries() {
    }

    /** The alpha-2 code for "KE", "KEN" or "Kenya"; empty when unknown. */
    public static Optional<String> alpha2(Object value) {
        if (value == null || value.toString().isBlank()) return Optional.empty();
        return Optional.ofNullable(TO_ALPHA2.get(value.toString().trim().toUpperCase(Locale.ROOT)));
    }

    /** "KE" or "*", for rules; anything else is refused. */
    public static String ruleCountry(String value) {
        if (ANY.equals(value)) return ANY;
        return alpha2(value).orElseThrow(() -> new com.intact_id.util.exception.ApplicationExceptions.IntactBadRequestException(
                "Unknown country: " + value + " (use an ISO code such as KE, or * for any country)"));
    }

    public static String name(String alpha2) {
        return ANY.equals(alpha2) ? "Any other country" : new Locale("", alpha2).getDisplayCountry(Locale.ENGLISH);
    }

    /**
     * Whose law applies to a verification's data: a business's country of registration; a person's country of
     * residence, else nationality; else the merchant's own country.
     */
    public static Optional<String> ofData(boolean business, Map<String, Object> customerData, String companyCountry) {
        Map<String, Object> data = customerData == null ? Map.of() : customerData;
        Optional<String> own = business ? alpha2(data.get("country"))
                : alpha2(data.get("countryOfResidence")).or(() -> alpha2(data.get("nationality")));
        return own.or(() -> alpha2(companyCountry));
    }
}
