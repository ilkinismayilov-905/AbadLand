package az.aqroinvest.integration;

/** Outcome of one EKTİS call. reference is set only on success. */
public record EktisResult(boolean success, String reference, String responseJson) {}
