package org.kframework.krun.api;

import org.kframework.kil.Context;
import org.kframework.kil.Rule;

import java.util.Optional;

/**
 * Container for Generic Kil Rules.
 */
public abstract class KilRuleContainer {
    private Optional<Rule> rule;

    protected Context context;

    public KilRuleContainer(Rule rule, Context context) {
        this.rule = Optional.ofNullable(rule);
        this.context = context;
    }


    /**
     * To be implemented in the extending class.
     * When called, the function should return the kilRule, if already calculated,
     * or calculate it using the context and the backend Rule, and then return it.
     */

    public abstract Rule getKilTerm();

    public abstract boolean equals(Object obj);

    public abstract int hashCode();
}
