package az.aqroinvest.domain;

/** AI analysis state of a plot, derived for display (AI center); never stored. Order = precedence. */
public enum AiState { RUNNING, AVAILABLE, FAILED, SAMPLE, NONE }
