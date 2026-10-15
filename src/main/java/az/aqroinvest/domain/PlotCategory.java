package az.aqroinvest.domain;

/** Detailed plot category of a staff listing (SPEC "Plot listings"); purpose() keeps matching on ActivityType working. */
public enum PlotCategory {
    CROP_FARMING, HORTICULTURE, LIVESTOCK, GREENHOUSE, BEEKEEPING, PROCESSING, RESTAURANT, GUEST_HOUSE, HOTEL;

    public boolean isAgrotourism() {
        return this == RESTAURANT || this == GUEST_HOUSE || this == HOTEL;
    }

    /** Recommended crops are required for these (crop list covers field crops and orchards). */
    public boolean needsCrops() {
        return this == CROP_FARMING || this == HORTICULTURE || this == GREENHOUSE;
    }

    public ActivityType purpose() {
        return switch (this) {
            case CROP_FARMING -> ActivityType.CROP_FARMING;
            case HORTICULTURE -> ActivityType.HORTICULTURE;
            case LIVESTOCK, BEEKEEPING -> ActivityType.LIVESTOCK;
            default -> ActivityType.OTHER;
        };
    }

    /** Seed plots: category from the old purpose field. */
    public static PlotCategory of(ActivityType a) {
        return switch (a) {
            case CROP_FARMING -> CROP_FARMING;
            case HORTICULTURE -> HORTICULTURE;
            case LIVESTOCK -> LIVESTOCK;
            case OTHER -> PROCESSING;
        };
    }
}
