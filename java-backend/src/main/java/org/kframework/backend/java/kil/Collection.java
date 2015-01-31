// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.kil.ASTNode;


/**
 * A collection of {@link Term}s.
 *
 * @author AndreiS
 */
@SuppressWarnings("serial")
public abstract class Collection extends Term implements CollectionInternalRepresentation {

    /**
     * Represents the rest part of this {@code Collection} which is not
     * explicitly specified (that is, simply being referred as a variable in
     * whole).
     */
    protected final Variable frame;

    protected final TermContext context;

    /**
     * Creates an instance of class {@code Collection} given its kind and a
     * frame variable. If the given frame is non-null, the kind of the frame
     * must be equal to the kind of the instance.
     */
    protected Collection(Variable frame, Kind kind, TermContext context) {
        super(kind);
        this.context = context;

        assert frame == null || frame.kind() == kind
                : "unexpected kind " + frame.kind() + " for frame variable " + frame.name()
                + "; expected kind " + kind;

        this.frame = frame;
    }

    /**
     * Checks if this {@code Collection} contains a frame variable.
     */
    public final boolean hasFrame() {
        return frame != null;
    }

    /**
     * @return the frame variable of this {@code Collection} if there is one;
     *         otherwise, fail the assertion
     */
    public final Variable frame() {
        assert hasFrame();

        return frame;
    }

    public abstract java.util.Collection<Variable> collectionVariables();

    /**
     * Returns true if this {@code Collection} does not contain any content.
     */
    public boolean isEmpty() {
        return concreteSize() == 0 && isConcreteCollection();
    }

    /**
     * Returns the number of elements or entries of this {@code Collection}.
     */
    public abstract int concreteSize();

    /**
     * Returns true if this collection contains elements or entries, but does not contain patterns,
     * functions or variables.
     */
    public abstract boolean isConcreteCollection();

    @Override
    public final boolean isSymbolic() {
        return false;
    }

    @Override
    public KLabel constructorLabel(TermContext context) {
        return KLabelConstant.of(
                context.definition().context().dataStructureSortOf(sort().toFrontEnd()).constructorLabel(),
                context.definition().context());
    }

    @Override
    public KItem unit(TermContext context) {
        return KItem.of(
                KLabelConstant.of(
                        context.definition().context().dataStructureSortOf(sort().toFrontEnd()).unitLabel(),
                        context.definition().context()),
                KList.EMPTY,
                context);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

}
