// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.kore.compile;

import org.kframework.Collections;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Definition;
import org.kframework.definition.DefinitionTransformer;
import org.kframework.definition.HybridMemoizingModuleTransformer;
import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Production;
import org.kframework.definition.Sentence;
import org.kframework.kil.Attribute;
import org.kframework.kore.Sort;

import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;

/**
 * Created by dwightguth on 5/28/15.
 */
public class GenerateSortPredicateSyntax implements UnaryOperator<Definition> {

    private Module gen(Module mod, Module boolModule) {
        Set<Sentence> res = new HashSet<>();
        for (Sort sort : iterable(mod.definedSorts())) {
            Production prod = Production("is" + sort.name(), Sorts.Bool(),
                    Seq(Terminal("is" + sort.name()), Terminal("("), NonTerminal(Sorts.K()), Terminal(")")),
                    Att().add(Attribute.FUNCTION_KEY).add(Attribute.PREDICATE_KEY, sort.name()));
            if (!mod.productions().contains(prod))
                res.add(prod);
        }
        if (!res.isEmpty()) {
            res.add(SyntaxSort(Sorts.K()));
        }
        return Module(mod.name(), Collections.add(boolModule, mod.imports()), (scala.collection.Set<Sentence>) mod.localSentences().$bar(immutable(res)), mod.att());
    }

    @Override
    public Definition apply(Definition definition) {
        return DefinitionTransformer.fromHybrid(m -> gen(m, definition.getModule("BOOL-SYNTAX").get()), "adding sort predicate productions").apply(definition);
    }
}
