// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.inject.Inject;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.kframework.attributes.Att;
import org.kframework.backend.java.kore.compile.ExpandMacrosDefinitionTransformer;
import org.kframework.compile.CleanKSeq;
import org.kframework.compile.ConfigurationInfoFromModule;
import org.kframework.definition.Constructors;
import org.kframework.definition.Definition;
import org.kframework.definition.DefinitionTransformer;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.Kompile;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.ADT;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KORE;
import org.kframework.kore.KSequence;
import org.kframework.kore.KVariable;
import org.kframework.kore.SortedADT;
import org.kframework.kore.compile.Backend;
import org.kframework.kore.compile.ConvertDataStructureToLookup;
import org.kframework.kore.compile.MergeRules;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.TransformKORE;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.kframework.definition.Constructors.*;
import static scala.compat.java8.JFunction.*;

public class JavaBackend implements Backend {

    private final KExceptionManager kem;
    private final FileUtil files;
    private final GlobalOptions globalOptions;
    private final KompileOptions kompileOptions;

    @Override
    public void accept(CompiledDefinition def) {
    }

    @Inject
    public JavaBackend(KExceptionManager kem, FileUtil files, GlobalOptions globalOptions, KompileOptions kompileOptions) {
        this.kem = kem;
        this.files = files;
        this.globalOptions = globalOptions;
        this.kompileOptions = kompileOptions;
    }

    @Override
    public Function<Definition, Definition> steps(Kompile kompile) {
        DefinitionTransformer convertDataStructureToLookup = DefinitionTransformer.fromSentenceTransformer(func((m, s) -> new ConvertDataStructureToLookup(m, false).convert(s)), "convert data structures to lookups");
        ExpandMacrosDefinitionTransformer expandMacrosDefinitionTransformer = new ExpandMacrosDefinitionTransformer(kem, files, globalOptions, kompileOptions);

        if (kompile.kompileOptions.experimental.koreProve) {
            return kompile.defaultSteps()
                    .andThen(expandMacrosDefinitionTransformer::apply)
                    .andThen(convertDataStructureToLookup::apply);
        }

        return d -> (func((Definition dd) -> kompile.defaultSteps().apply(dd)))
                .andThen(DefinitionTransformer.fromRuleBodyTranformer(RewriteToTop::bubbleRewriteToTopInsideCells, "bubble out rewrites below cells"))
                //.andThen(DefinitionTransformer.fromRuleBodyTranformer(RewriteToTop::bubbleRewriteOutOfKSeq, "bubble rewrites out of kseq"))
                .andThen(func(dd -> expandMacrosDefinitionTransformer.apply(dd)))
                .andThen(convertDataStructureToLookup)
                .andThen(DefinitionTransformer.fromRuleBodyTranformer(JavaBackend::ADTKVariableToSortedVariable, "ADT.KVariable to SortedVariable"))
                .andThen(DefinitionTransformer.fromRuleBodyTranformer(JavaBackend::convertKSeqToKApply, "kseq to kapply"))
                .andThen(DefinitionTransformer.fromRuleBodyTranformer(CleanKSeq.self(), "normalize kseq"))
                .andThen(DefinitionTransformer.fromSentenceTransformer(JavaBackend::markVariableKLabelRules, "mark rules with KLabels that are variables"))
                .andThen(func(dd -> markRegularRules(dd)))
                .andThen(DefinitionTransformer.fromSentenceTransformer(JavaBackend::markSingleVariables, "mark single variables"))
                .andThen(new DefinitionTransformer(new MergeRules(KORE.c())))
                .apply(d);
    }

    private static Definition markRegularRules(Definition d) {
        ConfigurationInfoFromModule configInfo = new ConfigurationInfoFromModule(d.mainModule());
        return DefinitionTransformer.fromSentenceTransformer((Sentence s) -> {
            if (s instanceof org.kframework.definition.Rule) {
                org.kframework.definition.Rule r = (org.kframework.definition.Rule) s;
                if (r.body() instanceof KApply && d.mainModule().sortFor().apply(((KApply) r.body()).klabel()).equals(configInfo.topCell()) && !r.att().contains(Att.varLabel())) {
                    return org.kframework.definition.Rule.apply(r.body(), r.requires(), r.ensures(), r.att().add(Att.topRule()));
                } else
                    return r;
            } else
                return s;
        }, "mark regular rules").apply(d);
    }

    private static Sentence markVariableKLabelRules(Sentence s) {
        if (s instanceof Rule) {
            Rule r = (Rule) s;
            MutableBoolean containsVarLabel = new MutableBoolean(false);
            //check for variable KLabel
            VisitKORE varlabelChecker = new VisitKORE() {
                @Override
                public Void apply(KApply k) {
                    KLabel label = k.klabel();
                    if (label instanceof KVariable) {
                        containsVarLabel.setTrue();
                        return null;
                    }
                    k.klist().stream().map(x -> apply(x));
                    return null;
                }
            };
            varlabelChecker.apply(r.body());
            varlabelChecker.apply(r.ensures());
            varlabelChecker.apply(r.requires());
            if (containsVarLabel.isTrue()) {
                return new Rule(r.body(), r.requires(), r.ensures(), r.att().add(Att.varLabel()));
            }
            return r;
        }
        return s;
    }

    private static K ADTKVariableToSortedVariable(K ruleBody) {
        return new TransformKORE() {
            public K apply(KVariable kvar) {
                return new SortedADT.SortedKVariable(kvar.name(), kvar.att());
            }
        }.apply(ruleBody);
    }

    private static K convertKSeqToKApply(K ruleBody) {
        return new TransformKORE() {
            public K apply(KSequence kseq) {
                return ((ADT.KSequence) kseq).kApply();
            }
        }.apply(ruleBody);
    }

    private static Sentence markSingleVariables(Sentence s) {
        if (s instanceof Rule) {
            Rule r = (Rule) s;

            if (!r.att().contains(Att.topRule()))
                return r;

            Map<KVariable, Integer> varCount = new HashMap<>();
            VisitKORE markerVisitor = new VisitKORE() {
                public Void apply(KVariable kvar) {
                    varCount.put(kvar, varCount.getOrDefault(kvar, 0) + 1);
                    return null;
                }
            };
            markerVisitor.apply(r.body());
            markerVisitor.apply(r.requires());
            markerVisitor.apply(r.ensures());

            TransformKORE markerAdder = new TransformKORE() {
                public K apply(KVariable kvar) {
                    if (kvar instanceof SortedADT.SortedKVariable && ((SortedADT.SortedKVariable) kvar).sort().equals(KORE.Sort("K")) && varCount.get(kvar) == 1) {
                        return new SortedADT.SortedKVariable("THE_VARIABLE", Att());
                    } else {
                        return kvar;
                    }
                }
            };

            return Constructors.Rule(markerAdder.apply(r.body()), markerAdder.apply(r.requires()), markerAdder.apply(r.ensures()), r.att());
        } else {
            return s;
        }
    }
}
