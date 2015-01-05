// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.kil.Bottom;
import org.kframework.backend.java.kil.BuiltinList;
import org.kframework.backend.java.kil.BuiltinMap;
import org.kframework.backend.java.kil.BuiltinSet;
import org.kframework.backend.java.kil.CellCollection;
import org.kframework.backend.java.kil.Hole;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.KLabelInjection;
import org.kframework.backend.java.kil.KList;
import org.kframework.backend.java.kil.KSequence;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.Token;


/**
 * Interface for a matcher. A matcher implements a visitor pattern specialized for pattern matching,
 * which uses double dispatch to reduce an invocation of {@code match} with two generic {@link
 * Term} arguments to an invocation of {@code match} with the first argument of the actual class.
 *
 * @author YilongL
 */
public interface Matcher {

    public String getName();

    public void match(Bottom bottom, Term pattern);
    public void match(BuiltinList builtinList, Term pattern);
    public void match(BuiltinMap builtinMap, Term pattern);
    public void match(BuiltinSet builtinSet, Term pattern);
    public void match(CellCollection cellCollection, Term pattern);
    public void match(Hole hole, Term pattern);
    public void match(KItem kItem, Term pattern);
    public void match(KLabelConstant kLabelConstant, Term pattern);
    public void match(KLabelInjection kLabelInjection, Term pattern);
    public void match(KList kList, Term pattern);
    public void match(KSequence kSequence, Term pattern);
    public void match(Term subject, Term pattern);
    public void match(Token token, Term pattern);

}
