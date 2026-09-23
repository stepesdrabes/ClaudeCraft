package dev.claudecraft.agent;

import java.util.List;

public final class Usage {
    private Usage() {
    }

    public static final class Context {
        private final long used;
        private final long max;

        public Context(long used, long max) {
            this.used = used;
            this.max = max;
        }

        public long used() {
            return used;
        }

        public long max() {
            return max;
        }

        public int percent() {
            return max <= 0 ? 0 : (int) Math.min(100, used * 100 / max);
        }
    }

    public static final class Limit {
        private final String label;
        private final int percent;
        private final long resetsAt;

        public Limit(String label, int percent, long resetsAt) {
            this.label = label;
            this.percent = percent;
            this.resetsAt = resetsAt;
        }

        public String label() {
            return label;
        }

        public int percent() {
            return percent;
        }

        public long resetsAt() {
            return resetsAt;
        }
    }

    public static final class Plan {
        private final List<Limit> limits;

        public Plan(List<Limit> limits) {
            this.limits = limits;
        }

        public List<Limit> limits() {
            return limits;
        }
    }
}
