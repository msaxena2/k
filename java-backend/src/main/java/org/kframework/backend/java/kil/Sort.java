// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.trie.PatriciaTrie;

import com.google.common.collect.ImmutableSet;

/**
 * Sort of a {@link Term}.
 *
 * @author YilongL
 *
 */
public final class Sort implements MaximalSharing, Serializable, org.kframework.kore.Sort {

    private static final Map<String, Sort> cache = Collections.synchronizedMap(new PatriciaTrie<>());

    public static final Sort KITEM          =   Sort.of("KItem");
    public static final Sort KSEQUENCE      =   Sort.of("K");
    public static final Sort KLIST          =   Sort.of("KList");
    public static final Sort KLABEL         =   Sort.of("KLabel");
    public static final Sort KRESULT        =   Sort.of("KResult");

    public static final Sort BAG            =   Sort.of("Bag");
    public static final Sort BAG_ITEM       =   Sort.of("BagItem");
    public static final Sort LIST           =   Sort.of("List");
    public static final Sort MAP            =   Sort.of("Map");
    public static final Sort SET            =   Sort.of("Set");

    public static final Sort INT            =   Sort.of("Int");
    public static final Sort BOOL           =   Sort.of("Bool");
    public static final Sort FLOAT          =   Sort.of("Float");
    public static final Sort CHAR           =   Sort.of("Char");
    public static final Sort STRING         =   Sort.of("String");
    public static final Sort BIT_VECTOR     =   Sort.of("MInt");
    public static final Sort META_VARIABLE  =   Sort.of("MetaVariable");

    public static final Sort VARIABLE       =   Sort.of("Variable");

    public static final Sort BOTTOM         =   Sort.of("Bottom");
    public static final Sort SHARP_BOT      =   Sort.of("#Bot");
    public static final Sort MGU            =   Sort.of("Mgu");

    /**
     * {@code String} representation of this {@code Sort}.
     */
    private final String name;

    /**
     * Gets the corresponding {@code Sort} from its {@code String}
     * representation.
     *
     * @param name
     *            the name of the sort
     * @return the sort
     */
    public static Sort of(String name) {
        return cache.computeIfAbsent(name, Sort::new);
    }

    public static Sort of(org.kframework.kil.Sort sort) {
        return of(sort.getName());
    }

    public static Set<Sort> of(Collection<org.kframework.kil.Sort> sorts) {
        ImmutableSet.Builder<Sort> builder = ImmutableSet.builder();
        for (org.kframework.kil.Sort name : sorts) {
            builder.add(Sort.of(name.getName()));
        }
        return builder.build();
    }

    private Sort(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Sort getUserListSort(String separator) {
        return Sort.of(org.kframework.kil.Sort.LIST_OF_BOTTOM_PREFIX + name
                + "{\"" + separator + "\"}");
    }

    public org.kframework.kil.Sort toFrontEnd() {
        return org.kframework.kil.Sort.of(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return this == object;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the cached instance rather than the de-serialized instance if
     * there is a cached instance.
     */
    Object readResolve() throws ObjectStreamException {
        return cache.computeIfAbsent(name, x -> this);
    }

}
