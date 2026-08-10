package com.intact_id.constant.enums;

import lombok.Getter;

@Getter
public enum Country {

    ALGERIA("DZ", "DZA", 12, "Algeria", "AFRICA", "NORTH"),
    EGYPT("EG", "EGY", 818, "Egypt", "AFRICA", "NORTH"),
    LIBYA("LY", "LBY", 434, "Libya", "AFRICA", "NORTH"),
    MOROCCO("MA", "MAR", 504, "Morocco", "AFRICA", "NORTH"),
    SUDAN("SD", "SDN", 729, "Sudan", "AFRICA", "NORTH"),
    TUNISIA("TN", "TUN", 788, "Tunisia", "AFRICA", "NORTH"),
    WESTERN_SAHARA("EH", "ESH", 732, "Western Sahara", "AFRICA", "NORTH"),

    BURUNDI("BI", "BDI", 108, "Burundi", "AFRICA", "EAST"),
    COMOROS("KM", "COM", 174, "Comoros", "AFRICA", "EAST"),
    DJIBOUTI("DJ", "DJI", 262, "Djibouti", "AFRICA", "EAST"),
    ERITREA("ER", "ERI", 232, "Eritrea", "AFRICA", "EAST"),
    ETHIOPIA("ET", "ETH", 231, "Ethiopia", "AFRICA", "EAST"),
    KENYA("KE", "KEN", 404, "Kenya", "AFRICA", "EAST"),
    MADAGASCAR("MG", "MDG", 450, "Madagascar", "AFRICA", "EAST"),
    MALAWI("MW", "MWI", 454, "Malawi", "AFRICA", "EAST"),
    MAURITIUS("MU", "MUS", 480, "Mauritius", "AFRICA", "EAST"),
    MOZAMBIQUE("MZ", "MOZ", 508, "Mozambique", "AFRICA", "EAST"),
    RWANDA("RW", "RWA", 646, "Rwanda", "AFRICA", "EAST"),
    SEYCHELLES("SC", "SYC", 690, "Seychelles", "AFRICA", "EAST"),
    SOMALIA("SO", "SOM", 706, "Somalia", "AFRICA", "EAST"),
    SOUTH_SUDAN("SS", "SSD", 728, "South Sudan", "AFRICA", "EAST"),
    TANZANIA("TZ", "TZA", 834, "Tanzania", "AFRICA", "EAST"),
    UGANDA("UG", "UGA", 800, "Uganda", "AFRICA", "EAST"),
    ZAMBIA("ZM", "ZMB", 894, "Zambia", "AFRICA", "EAST"),
    ZIMBABWE("ZW", "ZWE", 716, "Zimbabwe", "AFRICA", "EAST"),

    BENIN("BJ", "BEN", 204, "Benin", "AFRICA", "WEST"),
    BURKINA_FASO("BF", "BFA", 854, "Burkina Faso", "AFRICA", "WEST"),
    CABO_VERDE("CV", "CPV", 132, "Cabo Verde", "AFRICA", "WEST"),
    COTE_D_IVOIRE("CI", "CIV", 384, "Côte d'Ivoire", "AFRICA", "WEST"),
    GAMBIA("GM", "GMB", 270, "Gambia", "AFRICA", "WEST"),
    GHANA("GH", "GHA", 288, "Ghana", "AFRICA", "WEST"),
    GUINEA("GN", "GIN", 324, "Guinea", "AFRICA", "WEST"),
    GUINEA_BISSAU("GW", "GNB", 624, "Guinea-Bissau", "AFRICA", "WEST"),
    LIBERIA("LR", "LBR", 430, "Liberia", "AFRICA", "WEST"),
    MALI("ML", "MLI", 466, "Mali", "AFRICA", "WEST"),
    MAURITANIA("MR", "MRT", 478, "Mauritania", "AFRICA", "WEST"),
    NIGER("NE", "NER", 562, "Niger", "AFRICA", "WEST"),
    NIGERIA("NG", "NGA", 566, "Nigeria", "AFRICA", "WEST"),
    SENEGAL("SN", "SEN", 686, "Senegal", "AFRICA", "WEST"),
    SIERRA_LEONE("SL", "SLE", 694, "Sierra Leone", "AFRICA", "WEST"),
    TOGO("TG", "TGO", 768, "Togo", "AFRICA", "WEST"),

    ANGOLA("AO", "AGO", 24, "Angola", "AFRICA", "CENTRAL"),
    CAMEROON("CM", "CMR", 120, "Cameroon", "AFRICA", "CENTRAL"),
    CENTRAL_AFRICAN_REPUBLIC("CF", "CAF", 140, "Central African Republic", "AFRICA", "CENTRAL"),
    CHAD("TD", "TCD", 148, "Chad", "AFRICA", "CENTRAL"),
    CONGO("CG", "COG", 178, "Congo", "AFRICA", "CENTRAL"),
    DR_CONGO("CD", "COD", 180, "Democratic Republic of the Congo", "AFRICA", "CENTRAL"),
    EQUATORIAL_GUINEA("GQ", "GNQ", 226, "Equatorial Guinea", "AFRICA", "CENTRAL"),
    GABON("GA", "GAB", 266, "Gabon", "AFRICA", "CENTRAL"),
    SAO_TOME_PRINCIPE("ST", "STP", 678, "Sao Tome and Principe", "AFRICA", "CENTRAL"),

    BOTSWANA("BW", "BWA", 72, "Botswana", "AFRICA", "SOUTHERN"),
    ESWATINI("SZ", "SWZ", 748, "Eswatini", "AFRICA", "SOUTHERN"),
    LESOTHO("LS", "LSO", 426, "Lesotho", "AFRICA", "SOUTHERN"),
    NAMIBIA("NA", "NAM", 516, "Namibia", "AFRICA", "SOUTHERN"),
    SOUTH_AFRICA("ZA", "ZAF", 710, "South Africa", "AFRICA", "SOUTHERN");

    private final String iso2;
    private final String iso3;
    private final int numeric;
    private final String name;
    private final String continent;
    private final String region;

    Country(String iso2, String iso3, int numeric, String name, String continent, String region) {
        this.iso2 = iso2;
        this.iso3 = iso3;
        this.numeric = numeric;
        this.name = name;
        this.continent = continent;
        this.region = region;
    }
}
