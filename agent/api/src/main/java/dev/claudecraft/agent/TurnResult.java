package dev.claudecraft.agent;

public final class TurnResult {
    public enum Outcome { COMPLETED, INTERRUPTED, FAILED }

    private final Outcome outcome;
    private final String summary;
    private final String error;
    private final long durationMillis;
    private final double costUsd;

    public TurnResult(Outcome outcome, String summary, String error, long durationMillis, double costUsd) {
        this.outcome = outcome;
        this.summary = summary;
        this.error = error;
        this.durationMillis = durationMillis;
        this.costUsd = costUsd;
    }

    public Outcome outcome() {
        return outcome;
    }

    public String summary() {
        return summary;
    }

    public String error() {
        return error;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public double costUsd() {
        return costUsd;
    }
}
