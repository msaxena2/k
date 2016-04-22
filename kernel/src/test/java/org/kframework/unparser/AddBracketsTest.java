// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.unparser;

import org.junit.Before;
import org.junit.Test;
import org.kframework.attributes.Source;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.kompile.Kompile;
import org.kframework.kore.K;
import org.kframework.parser.ProductionReference;
import org.kframework.parser.TreeNodesToKORE;
import org.kframework.parser.concrete2kore.ParseInModule;
import org.kframework.parser.concrete2kore.ParserUtils;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.utils.errorsystem.ParseFailedException;
import org.kframework.utils.file.FileUtil;
import scala.Tuple2;
import scala.util.Either;

import java.io.File;
import java.util.Set;

import static org.junit.Assert.*;

public class AddBracketsTest {

    private String baseKText;

    @Before
    public void setUp() throws Exception {
        FileUtil files = FileUtil.testFileUtil();
        File definitionFile = new File(Kompile.BUILTIN_DIRECTORY.toString() + "/kast.k");
        baseKText = files.loadFromWorkingDirectory(definitionFile.getPath());
    }

    private Module parseModule(String def) {
        return ParserUtils.parseMainModuleOuterSyntax(def, Source.apply("generated by AddBracketsTest"), "TEST");
    }


    private String unparseTerm(K input, Module test) {
        return KOREToTreeNodes.toString(new AddBrackets(test).addBrackets((ProductionReference) KOREToTreeNodes.apply(KOREToTreeNodes.up(test, input), test)));
    }


    @Test
    public void testLambda() {
        String def = "module TEST\n" +
                "  syntax Val ::= Id\n" +
                "               | \"lambda\" Id \".\" Exp\n" +
                "  syntax Exp ::= Val\n" +
                "               | Exp Exp      [left]\n" +
                "               | \"(\" Exp \")\"  [bracket]\n" +
                "  syntax Id ::= r\"(?<![A-Za-z0-9\\\\_])[A-Za-z\\\\_][A-Za-z0-9\\\\_]*\"     [token, autoReject]\n" +
                "endmodule\n";
        unparserTest(def, "( lambda z . ( z z ) ) lambda x . lambda y . ( x y )");
        unparserTest(def, "a_ ( ( lambda x . lambda y . x ) y z )");
    }

    @Test
    public void testPriorityAndAssoc() {
        String def = "module TEST\n" +
                "  syntax Exp ::= Exp \"+\" Exp [left]\n" +
                "  syntax Exp ::= Exp \"*\" Exp [left]\n" +
                "  syntax Exp ::= \"1\"\n" +
                "  syntax Exp ::= \"(\" Exp \")\" [bracket]\n" +
                "  syntax priority _*_ > _+_\n" +
                "endmodule\n";
        unparserTest(def, "1 + 1 + 1 + 1");
        unparserTest(def, "1 + ( 1 + 1 ) + 1");
        unparserTest(def, "1 + ( 1 + ( 1 + 1 ) )");
        unparserTest(def, "1 + 1 * 1");
        unparserTest(def, "( 1 + 1 ) * 1");
    }

    private void unparserTest(String def, String pgm) {
        Definition baseK = RuleGrammarGenerator.autoGenerateBaseKCasts(org.kframework.Definition.from(baseKText + def, "TEST"));
        Module test = baseK.getModule("TEST").get();
        ParseInModule parser = RuleGrammarGenerator.getCombinedGrammar(RuleGrammarGenerator.getRuleGrammar(test, baseK), true);
        K parsed = parseTerm(pgm, parser);
        String unparsed = unparseTerm(parsed, test);
        assertEquals(pgm, unparsed);
    }

    private K parseTerm(String pgm, ParseInModule parser) {
        Tuple2<Either<Set<ParseFailedException>, K>, Set<ParseFailedException>> result = parser.parseString(pgm, Sorts.K(), Source.apply("generated by AddBracketsTest"));
        assertEquals(0, result._2().size());
        return TreeNodesToKORE.down(result._1().right().get());
    }

}
