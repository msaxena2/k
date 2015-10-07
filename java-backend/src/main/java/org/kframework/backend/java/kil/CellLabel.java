// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.collections4.trie.PatriciaTrie;
import org.kframework.compile.transformers.Cell2DataStructure;
import org.kframework.compile.utils.MetaK;

/**
 * Label of a cell.
 *
 * @author YilongL
 *
 */
public final class CellLabel implements MaximalSharing, Serializable {

    private static final Map<String, CellLabel> cache = Collections.synchronizedMap(new PatriciaTrie<>());

    public static final CellLabel GENERATED_TOP =   CellLabel.of(MetaK.Constants.generatedTopCellLabel);
    public static final CellLabel K             =   CellLabel.of("k");

    /**
     * {@code String} representation of this {@code CellLabel}.
     */
    private final String name;

    /**
     * Gets the corresponding {@code CellLabel} from its {@code String}
     * representation.
     *
     * @param name
     *            the name of the cell label
     * @return the cell label
     */
    public static CellLabel of(String name) {
        return cache.computeIfAbsent(name, CellLabel::new);
    }

    private CellLabel(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public boolean isMapCell() {
        return name.startsWith(Cell2DataStructure.MAP_CELL_CELL_LABEL_PREFIX);
    }

    public CellLabel getRealCellLabel() {
        assert isMapCell();
        return CellLabel.of(name.substring(Cell2DataStructure.MAP_CELL_CELL_LABEL_PREFIX.length()));
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
