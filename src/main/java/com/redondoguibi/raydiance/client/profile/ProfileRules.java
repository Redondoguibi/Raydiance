package com.redondoguibi.raydiance.client.profile;

import java.util.*;

/** Pure profile matcher: deterministic priority, wildcard selectors and wrapping day intervals. */
public final class ProfileRules {
    public record Context(String dimension, String biome, String weather, int time) {}
    public record Rule(String name, String dimension, String biome, String weather,
                       int start, int end, int priority, Map<String, Double> values) {
        public boolean matches(Context c) {
            int t = Math.floorMod(c.time(), 24000);
            return selector(dimension, c.dimension()) && selector(biome, c.biome())
                    && selector(weather, c.weather()) && (start == end ||
                    (start < end ? t >= start && t < end : t >= start || t < end));
        }
    }
    private static boolean selector(String filter, String value) { return "*".equals(filter) || filter.equals(value); }
    private static final Comparator<Rule> ORDER = Comparator.comparingInt(Rule::priority).reversed().thenComparing(Rule::name);

    public static Rule select(List<Rule> rules, Context context) {
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.matches(context) && (best == null || ORDER.compare(rule, best) < 0)) best = rule;
        }
        return best;
    }
    public static double blend(double current, double target, double seconds, double elapsed) {
        return seconds <= 0 ? target : current + (target - current) * blendFactor(seconds, elapsed);
    }

    static double blendFactor(double seconds, double elapsed) {
        return -Math.expm1(-Math.max(0, elapsed) / seconds);
    }
    private ProfileRules() {}
}
