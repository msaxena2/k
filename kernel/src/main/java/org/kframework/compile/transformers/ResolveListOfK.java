// Copyright (c) 2012-2015 K Team. All Rights Reserved.
package org.kframework.compile.transformers;

import org.kframework.kil.*;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KExceptionManager;
import java.util.ArrayList;
import java.util.Iterator;

/* TODO: andrei adds javadoc */
public class ResolveListOfK extends CopyOnWriteTransformer {

    public ResolveListOfK(org.kframework.kil.loader.Context context) {
        super("Resolve KList", context);
    }


    @Override
    public ASTNode visit(Syntax node, Void _void)  {
        return node;
    }

    @Override
    public ASTNode visit(TermCons node, Void _void)  {
        boolean change = false;
        ArrayList<Term> terms = new ArrayList<Term>();
        Production prod = node.getProduction();
        Iterator<Term> termIt = node.getContents().iterator();
        Term t;
        for (ProductionItem pitem : prod.getItems()) {
            if (pitem instanceof Terminal) continue;
            t = termIt.next();
            ASTNode resultAST = this.visitNode(t);
            if (resultAST != t) change = true;
            if (resultAST != null) {
                if (!(resultAST instanceof Term)) {
                    throw KExceptionManager.internalError(
                            "Expecting Term, but got " + resultAST.getClass() + ".",
                            this, t);
                }
                Term result = (Term) resultAST;
                if (pitem instanceof NonTerminal
                        && ((NonTerminal)pitem).getSort().equals(Sort.KLIST)
                        && !t.getSort().equals(Sort.KLIST)) {
                    KList list = new KList();
                    list.getContents().add(result);
                    result = list;
                    change = true;
                }
                terms.add(result);
            }
        }
        if (change) {
            node = node.shallowCopy();
            node.setContents(terms);
        }
        return visit((Term) node, _void);
    }

}
