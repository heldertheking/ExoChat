package com.heldertheking.exochat.app.permission;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/// The explicit decisions of one layer: a rule is allowed, denied or not listed (inherited). Immutable.
public final class RuleSet {
    private static final RuleSet EMPTY = new RuleSet(new LinkedHashMap<>());

    private final Map<Rule, RuleState> states;

    private RuleSet(Map<Rule, RuleState> states) {
        this.states = states;
    }

    /// A set that decides nothing.
    public static RuleSet empty() {
        return EMPTY;
    }

    /// A set with the given decisions.
    public static RuleSet of(Map<Rule, RuleState> states) {
        if (states.isEmpty()) {
            return EMPTY;
        }
        return new RuleSet(new LinkedHashMap<>(states));
    }

    /// A set that denies every rule.
    public static RuleSet denyingAll() {
        var states = new LinkedHashMap<Rule, RuleState>();
        for (var rule : Rule.all()) {
            states.put(rule, RuleState.DENY);
        }
        return new RuleSet(states);
    }

    /// A set that denies the given rules.
    public static RuleSet denying(Rule... rules) {
        var states = new LinkedHashMap<Rule, RuleState>();
        for (var rule : rules) {
            states.put(rule, RuleState.DENY);
        }
        return of(states);
    }

    /// A set that allows the given rules.
    public static RuleSet allowing(Rule... rules) {
        var states = new LinkedHashMap<Rule, RuleState>();
        for (var rule : rules) {
            states.put(rule, RuleState.ALLOW);
        }
        return of(states);
    }

    /// The decision about the rule, null if the set has none.
    public @Nullable RuleState get(Rule rule) {
        return states.get(rule);
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof RuleSet that && states.equals(that.states);
    }

    @Override
    public int hashCode() {
        return states.hashCode();
    }

    @Override
    public String toString() {
        return states.toString();
    }
}
