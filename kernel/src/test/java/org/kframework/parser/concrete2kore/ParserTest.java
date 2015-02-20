// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;
import org.kframework.builtin.Sorts;
import org.kframework.kore.Attributes;
import org.kframework.kore.Sort;
import org.kframework.kore.UninterpretedSort;
import org.kframework.kore.outer.Production;
import org.kframework.kore.outer.ProductionItem;
import org.kframework.parser.*;

import static org.kframework.Collections.*;
import static org.kframework.kore.Constructors.*;
import static org.kframework.kore.outer.Constructors.*;

import org.kframework.parser.concrete2kore.Grammar.NonTerminal;
import org.kframework.parser.concrete2kore.Grammar.NonTerminalState;
import org.kframework.parser.concrete2kore.Grammar.PrimitiveState;
import org.kframework.parser.concrete2kore.Grammar.RegExState;
import org.kframework.parser.concrete2kore.Grammar.RuleState;
import org.kframework.parser.concrete2kore.Rule.DeleteRule;
import org.kframework.parser.concrete2kore.Rule.WrapLabelRule;

public class ParserTest {
    /* public static void main(String[] args) {
        try {
            System.in.read();
            new ParserTest().testListOfTokens();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void setUp() throws Exception {
        super.setUp();

    }*/

    private static final Sort EXP_SORT = Sort("Exp");

    @Test
    public void testEmptyGrammar() throws Exception {
        Grammar grammar = new Grammar();
        NonTerminal nt1 = new NonTerminal("startNt");
        nt1.entryState.next.add(nt1.exitState);
        grammar.add(nt1);

        grammar.compile();

        Parser parser = new Parser("");
        Term result = parser.parse(grammar.get("startNt"), 0);
        Term expected = amb(klist(amb(KList.apply())));

        Assert.assertEquals("Empty Grammar check: ", expected, result);
        // the start and exit state of the NonTerminal
        grammar.add(nt1);
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testSingleToken() throws Exception {
        NonTerminal nt1 = new NonTerminal("StartNT");
        RegExState res = new RegExState("RegExStid", nt1, Pattern.compile("[a-zA-Z0-9]+"), null);
        Grammar grammar = new Grammar();
        grammar.add(nt1);
        nt1.entryState.next.add(res);
        res.next.add(nt1.exitState);

        grammar.compile();
        Parser parser = new Parser("asdfAAA1");

        Term result = parser.parse(nt1, 0);
        Term expected = amb(klist(amb(klist(new Constant("asdfAAA1", null, Optional.empty())))));
        Assert.assertEquals("Single Token check: ", expected, result);
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(nt1));
    }

    @Test
    public void testSequenceOfTokens() throws Exception {
        // A ::= #token{"[a-zA-Z0-9]+ +"} #token{"[a-zA-Z0-9]+"} [klabel(seq)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState res1 = new RegExState("RegExStid", nt1, Pattern.compile("[a-zA-Z0-9]+ +"), null);
        RegExState res2 = new RegExState("RegExStid2", nt1, Pattern.compile("[a-zA-Z0-9]+"), null);
        RuleState rs = new RuleState("RuleStateId", nt1, new WrapLabelRule(label("seq")));

        nt1.entryState.next.add(res1);
        res1.next.add(res2);
        res2.next.add(rs);
        rs.next.add(nt1.exitState);
        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();
        Parser parser = new Parser("asdfAAA1 adfsf");

        Term result = parser.parse(nt1, 0);
        Term expected = amb(klist(amb(klist(kapp("seq", new Constant("asdfAAA1 ", null, Optional.empty()), new Constant("adfsf", null, Optional.empty()))))));
        Assert.assertEquals("Single Token check: ", expected, result);
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(nt1));
    }

    @Test
    public void testDisjunctionOfTokens() throws Exception {
        // A ::= #token{"[a-z0-9]+"} [klabel(s1)]
        //     | #token{"[A-Z0-2]+"} #token{"[3-9]*"} [klabel(s3)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState res1 = new RegExState("RegExStid", nt1, Pattern.compile("[a-z0-9]+"), null);
        RegExState res2 = new RegExState("RegExStid2", nt1, Pattern.compile("[A-Z0-2]+"), null);
        RegExState res3 = new RegExState("RegExStid3", nt1, Pattern.compile("[3-9]*"), null);

        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("s1")));
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("s3")));

        nt1.entryState.next.add(res1);
        nt1.entryState.next.add(res2);
        res1.next.add(rs1);
        rs1.next.add(nt1.exitState);
        res2.next.add(res3);
        res3.next.add(rs3);
        rs3.next.add(nt1.exitState);

        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("abc").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("s1", new Constant("abc", null, Optional.empty()))))));
            Assert.assertEquals("Single Token check: ", expected, result);
        }

        {
            Term result = new Parser("ABC").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("s3", new Constant("ABC", null, Optional.empty()), new Constant("", null, Optional.empty()))))));
            Assert.assertEquals("Single Token check: ", expected, result);
        }

        {
            Term result = new Parser("123").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("s1", new Constant("123", null, Optional.empty()))), klist(kapp("s3", new Constant("12", null, Optional.empty()), new Constant("3", null, Optional.empty()))))));
            Assert.assertEquals("Single Token check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(nt1));
    }

    @Test
    public void testListOfTokens() throws Exception {
        // A ::= ("[a-zA-Z0-9]")*  [klabel(seq)]

        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState res1 = new RegExState("RegExStid", nt1, Pattern.compile("[a-zA-Z0-9]"), null);
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("seq")));

        nt1.entryState.next.add(res1);
        nt1.entryState.next.add(rs3);
        res1.next.add(rs3);
        res1.next.add(res1);
        rs3.next.add(nt1.exitState);
        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("abc").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("seq", new Constant("a", null, Optional.empty()), new Constant("b", null, Optional.empty()), new Constant("c", null, Optional.empty()))))));
            Assert.assertEquals("Single Token check: ", expected, result);
        }

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("seq")))));
            Assert.assertEquals("Single Token check: ", expected, result);
        }

        /*
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append('a');
            }
            for (int j = 0; j < 20; j++) {
                long start = getCpuTime();
                for (int i = 0; i < 100; i++) {
                    Term result = new Parser(new ParseState(sb.toString())).parse(nt1, 0);
                }
                long end = getCpuTime();
                System.out.println("Time: " + ((end - start) / 1000000.0));
            }
        }
        */
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }
    public static long getCpuTime( ) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
        return bean.isCurrentThreadCpuTimeSupported( ) ?
                bean.getCurrentThreadCpuTime( ) : 0L;
    }

    @Test
    public void testNestedNonTerminals1() throws Exception {
        // A ::= ""    [klabel(epsilon)]
        //     | x A y [klabel(xAy)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resx = new RegExState("RegExStidx", nt1, Pattern.compile("x"), null);
        RegExState resy = new RegExState("RegExStidy", nt1, Pattern.compile("y"), null);
        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("xAy")));
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("epsilon")));

        NonTerminalState nts = new NonTerminalState("NT", nt1, nt1, false);

        nt1.entryState.next.add(resx);
        nt1.entryState.next.add(rs3);
        rs3.next.add(nt1.exitState);
        resx.next.add(nts);
        nts.next.add(resy);
        resy.next.add(rs1);
        rs1.next.add(nt1.exitState);

        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("epsilon")))));
            Assert.assertEquals("EmtpyString check: ", expected, result);
        }

        {
            Term result = new Parser("xxyy").parse(nt1, 0);
            Term expected =
                amb(klist(amb(klist(kapp("xAy",
                    new Constant("x", null, Optional.empty()),
                    amb(klist(kapp("xAy",
                        new Constant("x", null, Optional.empty()),
                        amb(klist(kapp("epsilon"))),
                            new Constant("y", null, Optional.empty())))),
                        new Constant("y", null, Optional.empty()))))));
            Assert.assertEquals("x^ny^n check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals2() throws Exception {
        // A ::= ""  [klabel(epsilon)]
        //     | A y [klabel(Ay)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resy = new RegExState("RegExStidy", nt1, Pattern.compile("y"), null);

        NonTerminalState nts = new NonTerminalState("NT", nt1, nt1, false);
        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("Ay")));
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("epsilon")));

        nt1.entryState.next.add(nts);
        nt1.entryState.next.add(rs3);
        rs3.next.add(nt1.exitState);
        nts.next.add(resy);
        resy.next.add(rs1);
        rs1.next.add(nt1.exitState);
        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("epsilon")))));
            Assert.assertEquals("EmtpyString check: ", expected, result);
        }

        {
            Term result = new Parser("yy").parse(nt1, 0);
            Term expected =
                amb(klist(amb(klist(kapp("Ay",
                    amb(klist(kapp("Ay",
                        amb(klist(kapp("epsilon"))),
                            new Constant("y", null, Optional.empty())))),
                        new Constant("y", null, Optional.empty()))))));
            Assert.assertEquals("y^n check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals3() throws Exception {
        // A ::= ""  [klabel(epsilon)]
        //     | x A [klabel(xA)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resx = new RegExState("RegExStidx", nt1, Pattern.compile("x"), null);

        NonTerminalState nts = new NonTerminalState("NT", nt1, nt1, false);
        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("xA")));
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("epsilon")));

        nt1.entryState.next.add(resx);
        nt1.entryState.next.add(rs3);
        rs3.next.add(nt1.exitState);
        resx.next.add(nts);
        nts.next.add(rs1);
        rs1.next.add(nt1.exitState);

        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("epsilon")))));
            Assert.assertEquals("EmtpyString check: ", expected, result);
        }

        {
            Term result = new Parser("xx").parse(nt1, 0);
            Term expected =
                amb(klist(amb(klist(kapp("xA",
                        new Constant("x", null, Optional.empty()),
                        amb(klist(kapp("xA",
                                new Constant("x", null, Optional.empty()),
                                amb(klist(kapp("epsilon")))))))))));
            Assert.assertEquals("x^n check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals4() throws Exception {
        // A ::= "x" [klabel(x)]
        //     | A A [klabel(AA)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resx = new RegExState("RegExStidx", nt1,Pattern.compile("x"), null);

        NonTerminalState nts1 = new NonTerminalState("NT1", nt1, nt1, false);
        NonTerminalState nts2 = new NonTerminalState("NT2", nt1, nt1, false);

        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("x")));
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("AA")));


        nt1.entryState.next.add(resx);
        nt1.entryState.next.add(nts1);

        resx.next.add(rs1);
        rs1.next.add(nt1.exitState);

        nts1.next.add(nts2);
        nts2.next.add(rs3);
        rs3.next.add(nt1.exitState);

        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("x").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("x", new Constant("x", null, Optional.empty()))))));
            Assert.assertEquals("Single char check: ", expected, result);
        }

        {
            Term result = new Parser("xx").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("AA", amb(klist(kapp("x", new Constant("x", null, Optional.empty())))), amb(klist(kapp("x", new Constant("x", null, Optional.empty())))))))));
            Assert.assertEquals("AA check: ", expected, result);
        }
        Term X = kapp("x", new Constant("x", null, Optional.empty()));
        {
            Term result = new Parser("xxx").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("AA", amb(klist(kapp("AA", amb(klist(X)), amb(klist(X))))), amb(klist(X)))),
                                          klist(kapp("AA", amb(klist(X)), amb(klist(kapp("AA", amb(klist(X)), amb(klist(X))))))))));
            Assert.assertEquals("AAA check: ", expected, result);
        }
        {
            Term result = new Parser("xxxx").parse(nt1, 0);
            Term t1 = amb(klist(X));
            Term t2 = amb(klist(kapp("AA", t1, t1)));
            Term t3 = amb(klist(kapp("AA", t2, t1)), klist(kapp("AA", t1, t2)));
            Term t4 = amb(klist(kapp("AA", t3, t1)), klist(kapp("AA", t2, t2)), klist(kapp("AA", t1, t3)));
            Term expected = amb(klist(t4));
            Assert.assertEquals("AAA check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals5() throws Exception {
        // A1 ::= "x"  [klabel(x)]
        // A2 ::= A1   [klabel(n1)]
        // ....
        // An ::= An-1 [klabel(n[n-1])]
        // start symb is An
        NonTerminal baseCase = new NonTerminal("BaseCase");
        RegExState resx = new RegExState("X", baseCase, Pattern.compile("x"), null);
        RuleState rs1 = new RuleState("RuleStateId1", baseCase, new WrapLabelRule(label("x")));

        baseCase.entryState.next.add(resx);
        resx.next.add(rs1);
        rs1.next.add(baseCase.exitState);

        Term expected = amb(klist(kapp("x", new Constant("x", null, Optional.empty()))));

        for (int i = 2; i < 10; i++) {
            NonTerminal nt = new NonTerminal("NT"+i);
            NonTerminalState state = new NonTerminalState("S"+i, nt, baseCase, false);
            RuleState rs2 = new RuleState("RuleStateId" + i, nt, new WrapLabelRule(label("n" + i)));
            nt.entryState.next.add(state);
            state.next.add(rs2);
            rs2.next.add(nt.exitState);
            baseCase = nt;
            expected = amb(klist(kapp("n" + i, expected)));
        }
        Grammar grammar = new Grammar();
        grammar.add(baseCase);
        grammar.compile();

        {
            Term result = new Parser("x").parse(baseCase, 0);
            expected = amb(klist(expected));
            Assert.assertEquals("Single char check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(baseCase.entryState) && nc.isNullable(baseCase.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(baseCase));
    }

    @Test
    public void testNestedNonTerminals6() throws Exception {
        // A1 ::= ""  [klabel(x)]
        // A2 ::= A1   [klabel(n1)]
        // ....
        // An ::= An-1 [klabel(n[n-1])]
        // start symb is An

        NonTerminal baseCase = new NonTerminal("BaseCase");
        RegExState resx = new RegExState("X", baseCase, Pattern.compile(""), null);
        RuleState rs1 = new RuleState("RuleStateId1", baseCase, new WrapLabelRule(label("x")));

        baseCase.entryState.next.add(resx);
        resx.next.add(rs1);
        rs1.next.add(baseCase.exitState);

        Term expected = amb(klist(kapp("x", new Constant("", null, Optional.empty()))));

        for (int i = 2; i < 10; i++) {
            NonTerminal nt = new NonTerminal("NT"+i);
            NonTerminalState state = new NonTerminalState("S"+i, nt, baseCase, false);
            RuleState rs2 = new RuleState("RuleStateId" + i, nt, new WrapLabelRule(label("n" + i)));
            nt.entryState.next.add(state);
            state.next.add(rs2);
            rs2.next.add(nt.exitState);
            baseCase = nt;
            expected = amb(klist(kapp("n" + i, expected)));
        }
        Grammar grammar = new Grammar();
        grammar.add(baseCase);
        grammar.compile();

        {
            Term result = new Parser("").parse(baseCase, 0);
            expected = amb(klist(expected));
            Assert.assertEquals("Single char check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(baseCase.entryState) && nc.isNullable(baseCase.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(baseCase));
    }

    @Test
    public void testArithmeticLanguage() throws Exception {
        // Lit  ::= Token{[0-9]+}[klabel(lit)]
        // Term ::= "(" Exp ")"  [klabel(bracket)]
        //        | Term "*" Lit [klabel(mul)]
        //        | Lit
        // Exp  ::= Exp "+" Term [klabel(plus)]
        //        | Term
        NonTerminal lit = new NonTerminal("Lit");
        NonTerminal trm = new NonTerminal("Trm");
        NonTerminal exp = new NonTerminal("Exp");

        { // lit
            RegExState litState = new RegExState("LitState", lit, Pattern.compile("[0-9]+"), null);
            RuleState rs1 = new RuleState("RuleStateId1", lit, new WrapLabelRule(label("lit")));
            lit.entryState.next.add(litState);
            litState.next.add(rs1);
            rs1.next.add(lit.exitState);
        }

        { // trm
            RegExState lparen = new RegExState("LParen", trm, Pattern.compile("\\("), null);
            RegExState rparen = new RegExState("RParen", trm, Pattern.compile("\\)"), null);
            RuleState rs1 = new RuleState("RuleStateId1", trm, new WrapLabelRule(label("bracket")));

            RegExState star = new RegExState("Star", trm, Pattern.compile("\\*"), null);
            NonTerminalState expState = new NonTerminalState("Trm->Exp", trm, exp, false);
            NonTerminalState trmState = new NonTerminalState("Trm->Trm", trm, trm, false);
            NonTerminalState lit1State = new NonTerminalState("Trm->Lit1", trm, lit, false);
            RuleState rs2 = new RuleState("RuleStateId2", trm, new WrapLabelRule(label("mul")));

            NonTerminalState lit2State = new NonTerminalState("Trm->Lit2", trm, lit, false);

            trm.entryState.next.add(lparen);
            lparen.next.add(expState);
            expState.next.add(rparen);
            rparen.next.add(rs1);
            rs1.next.add(trm.exitState);

            trm.entryState.next.add(trmState);
            trmState.next.add(star);
            star.next.add(lit1State);
            lit1State.next.add(rs2);
            rs2.next.add(trm.exitState);

            trm.entryState.next.add(lit2State);
            lit2State.next.add(trm.exitState);
        }

        { // exp
            RegExState plus = new RegExState("Plus", exp, Pattern.compile("\\+"), null);
            NonTerminalState expState = new NonTerminalState("Exp->Exp", exp, exp, false);
            NonTerminalState trm1State = new NonTerminalState("Exp->Trm1", exp, trm, false);
            RuleState rs1 = new RuleState("RuleStateId3", exp, new WrapLabelRule(label("plus")));
            NonTerminalState trm2State = new NonTerminalState("Exp->Trm2", exp, trm, false);

            exp.entryState.next.add(expState);
            expState.next.add(plus);
            plus.next.add(trm1State);
            trm1State.next.add(rs1);
            rs1.next.add(exp.exitState);

            exp.entryState.next.add(trm2State);
            trm2State.next.add(exp.exitState);
        }

        Grammar grammar = new Grammar();
        grammar.add(exp);
        grammar.compile();
        {
            Term result = new Parser("1+2*3").parse(exp, 0);
            Term expected = amb(klist(amb(klist(kapp("plus", amb(klist(amb(klist(amb(klist(kapp("lit", token("1")))))))),
                                                token("+"),
                                                amb(klist(kapp("mul", amb(klist(amb(klist(kapp("lit", token("2")))))),
                                                          token("*"),
                                                          amb(klist(kapp("lit", token("3"))))))))))));
            Assert.assertEquals("1+2*3: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(exp.entryState) && nc.isNullable(exp.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(exp));

    }

//    public void testPrecedence1() throws Exception {
//        NonTerminal e = new NonTerminal(new NonTerminalId("E"),
//            new StateId("E-entry"), new State.OrderingInfo(?),
//            new StateId("E-exit"), new State.OrderingInfo(?));
//        { // lit
//            NextableState var = new RegExState(new StateId("Var"), e, new State.OrderingInfo(?), Pattern.compile("[a-z]"));
//            NextableState varLabel = new WrapLabelRuleState(new StateId("VarLabel"), e, new State.OrderingInfo(?), new KLabelConstant("Var"));
//            e.entryState.next.add(var);
//            var.next.add(varLabel);
//            varLabel.next.add(e.exitState);
//        }
//
//        { // plus
//            NextableState e1 = new NonTerminalState(new StateId("Plus-e1"), e, new State.OrderingInfo(?), e, false);
//            NextableState plus = new RegExState(new StateId("Plus-token"), e, new State.OrderingInfo(?), Pattern.compile("\\+"));
//            NextableState e2 = new NonTerminalState(new StateId("Plus-e1"), e, new State.OrderingInfo(?), e, false);
//            NextableState plusLabel = new WrapLabelRuleState(new StateId("Plus-label"), e, new State.OrderingInfo(?), new KLabelConstant("_+_"));
//            e.entryState.next.add(e1);
//            e1.next.add(plus);
//            plus.next.add(e2);
//            e2.next.add(plusLabel);
//            plusLabel.next.add(e.exitState);
//        }
//
//        { // times
//            NextableState e1 = new NonTerminalState(new StateId("Times-e1"), e, new State.OrderingInfo(?), e, false);
//            NextableState times = new RegExState(new StateId("Times-token"), e, new State.OrderingInfo(?), Pattern.compile("\\*"));
//            NextableState e2 = new NonTerminalState(new StateId("Times-e1"), e, new State.OrderingInfo(?), e, false);
//            NextableState timesLabel = new RuleState(new StateId("Times-label"), e, new State.OrderingInfo(?), new WrapLabelRule(new KLabelConstant("_*_")));
//            e.entryState.next.add(e1);
//            e1.next.add(times);
//            times.next.add(e2);
//            e2.next.add(timesLabel);
//            timesLabel.next.add(e.exitState);
//        }
//
//        {
//            Term result = new Parser(new ParseState("x+y*z")).parse(e, 0);
//            /*Term expected = amb(klist(amb(klist(amb(klist(amb(klist(amb(klist(token("1"))))))),
//                token("+"),
//                amb(klist(amb(klist(amb(klist(token("2"))))),
//                    token("*"),
//                    amb(klist(token("3")))))))));*/
//            assertEquals("1+2*3: ", null, result);
//            // lookahead success
//            // lookahead failure
//            // require context
//            // reduce = Adopt
//            // insert
//            // delete = Done
//            // assoc
//            // prec
//            // prefer
//        }
//
//    }

/*
    public static void main(String[] args) {
        try {
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }

        NonTerminalId ntistart = new NonTerminalId("StartNT");
        StateId stistart = new StateId("StartState");
        StateId stiend = new StateId("EndState");

        NonTerminal nt1 = new NonTerminal(ntistart, stistart, new State.OrderingInfo(0), stiend, new State.OrderingInfo(100));

        RegExState res1 = new RegExState(new StateId("RegExStid"), nt1, new State.OrderingInfo(1), Pattern.compile("[a-zA-Z0-9]"));

        nt1.entryState.next.add(res1);
        nt1.entryState.next.add(nt1.exitState);
        res1.next.add(nt1.exitState);
        res1.next.add(res1);

        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append('a');
            }
            for (int j = 0; j < 10; j++) {
                long start = getCpuTime();
                for (int i = 0; i < 1; i++) {
                    Term result = new Parser(new ParseState(sb.toString())).parse(nt1, 0);
                }
                long end = getCpuTime();
                System.out.println("Time: " + ((end - start) / 1000000.0));
            }
        }
        try {
            System.in.read();
            System.in.read();
            System.in.read();
            System.in.read();
            System.in.read();
            System.in.read();
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // with proper implementation we are slow:
        //  - for a string of length 100, we are at 9.5 us per char
        //  - for a string of length 1000, we are at 65 us per char
        //  - for a string of length 10000, we are at 620 us per char
        // but with no AST construction we are getting no quadratic behavior and 1.6 micro seconds per character
        // - regex may slow things down
        // - computing Term.hashCode inside a function slows things down quite a bit
        // - constructing ASTs with long lists slows things down
        // - calling java.Object.hashCode is SLOOOOOOW
        // - calling RTTI versions of getStateCall, etc. are slow
    }

    public static long getCpuTime( ) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported( ) ?
                bean.getCurrentThreadCpuTime( ) : 0L;
    }
    */

    @Test
    public void testListAmbiguity() throws Exception {
        // Int
        // (|-----([\+-]?\d+)------|)
        NonTerminal intNt = new NonTerminal("Int");
        Production intProd09 = prod(Sort("Int"), RegexTerminal("[0-9]"));
        PrimitiveState ints = new RegExState("Int-State", intNt, Pattern.compile("[\\+-]?\\d+"), intProd09);
        intNt.entryState.next.add(ints);
        ints.next.add(intNt.exitState);

        // Exp
        /**
         * (|--+---[Int]------<wrap>--------------+---|)
         *     |                                  |
         *     +---("-")---<del>---[Exp]---<'-_>--+
         */
        NonTerminal expNt = new NonTerminal("Exp");

        NonTerminalState expInt = new NonTerminalState("Int-nts(Exp)", expNt, intNt, false);
        Production p22 = prod(EXP_SORT, NonTerminal(Sorts.Int()));
        RuleState rs2 = new RuleState("Exp-wrapInt", expNt, new WrapLabelRule(p22));
        expNt.entryState.next.add(expInt);
        expInt.next.add(rs2);
        rs2.next.add(expNt.exitState);

        PrimitiveState minus = new RegExState("Minus-State", expNt, Pattern.compile("-", Pattern.LITERAL), null);
        RuleState deleteToken = new RuleState("Minus-Delete", expNt, new DeleteRule(1, true));
        NonTerminalState expExp = new NonTerminalState("Exp-nts(Exp)", expNt, expNt, false);
        Production p1 = SyntaxProduction(EXP_SORT, Seq(Terminal("-"), NonTerminal(EXP_SORT)), Attributes().add("klabel", "'-_"));
        RuleState rs1 = new RuleState("Exps-wrapMinus", expNt, new WrapLabelRule(p1));
        expNt.entryState.next.add(minus);
        minus.next.add(deleteToken);
        deleteToken.next.add(expExp);
        expExp.next.add(rs1);
        rs1.next.add(expNt.exitState);

        // Exps
        /**
         * (|-------[Exp]--------------+----<'_,_>-------|)
         *            ^                |
         *            +--<del>--(",")--+
         */
        NonTerminal expsNt = new NonTerminal("Exps");
        NonTerminalState expExps = new NonTerminalState("Exp-nts(Exps)", expsNt, expNt, false);
        Production p2 = SyntaxProduction(Sort("Exps"), Seq(NonTerminal(EXP_SORT)), Attributes().add("klabel", "'_,_"));
        PrimitiveState separator = new RegExState("Sep-State", expsNt, Pattern.compile(",", Pattern.LITERAL), null);
        RuleState deleteToken2 = new RuleState("Separator-Delete", expsNt, new DeleteRule(1, true));
        RuleState labelList = new RuleState("RuleStateExps", expsNt, new WrapLabelRule(p2));
        expsNt.entryState.next.add(expExps);
        expExps.next.add(expExps); // circularity
        separator.next.add(deleteToken2);
        deleteToken2.next.add(expExps);
        expExps.next.add(labelList);
        labelList.next.add(expsNt.exitState);

        Grammar grammar = new Grammar();
        grammar.add(expsNt);
        grammar.compile();

        Term result = new Parser("-1").parse(expsNt, 0);
        //System.out.println(result);
        Term result2 = new TreeCleanerVisitor().apply(result).right().get();
        //System.out.println(result2);

        Term one = Constant.apply("1", intProd09, Optional.empty());
        Term mone = Constant.apply("-1", intProd09, Optional.empty());
        Term mexp = TermCons.apply(Arrays.asList(one), p1);
        Term expected = TermCons.apply(Arrays.<Term>asList(amb(mone, mexp)), p2);

        Assert.assertEquals("The error: ", expected, result2);
    }

    public static Ambiguity amb(Term ... terms) {
        return Ambiguity.apply(Arrays.asList(terms));
    }

    public static Constant token(String x) {
        return Constant.apply(x, null, Optional.empty());
    }

    public static Production prod(Sort sort, ProductionItem... pi) {
        return SyntaxProduction(sort, immutable(Arrays.<ProductionItem>asList(pi)));
    }

    public static TermCons kapp(String label, Term ... terms) {
        return TermCons.apply(Arrays.asList(terms), label(label), Optional.empty());
    }

    public static KList klist(Term ... terms) {
        return KList.apply(Arrays.asList(terms));
    }

    public static Production label(String x) {
        // using UserList to avoid arity checks
        return prod(Sorts.K(), NonTerminal(Sorts.K()));
    }
}
