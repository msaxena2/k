// Copyright (c) 2014-2015 K Team. All Rights Reserved.

package org.kframework.kore.convertors;

import java.io.IOException;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TstKILtoKOREIT extends BaseTest {

    @Test
    public void emptyModule() throws IOException {
        outerOnlyTest();
    }

    @Test
    public void simpleSyntax() throws IOException {
        outerOnlyTest();
    }

    @Test
    public void syntaxWithAttributes() throws IOException {
        outerOnlyTest();
    }

    @Test
    public void syntaxWithRhs() throws IOException {
        outerOnlyTest();
    }

    // we'll have to eventually convert the configuration
    // to macro rules, as Grigore wrote on the wiki
    // for now, we'll do this conversion:
    // <k foo="bla"> .K </k> becomes:
    // KApply(KLabel("k"), KList(EmptyK), Attributes(KApply(KLabel("foo",
    // KToken(String, "bla"))))
    @Test
    public void configuration() throws IOException {
        sdfTest();
    }

    @Test
    public void context() throws IOException {
        sdfTest();
    }

    @Test
    public void imports() throws IOException {
        outerOnlyTest();
    }

    @Test
    public void simpleRule() throws IOException {
        sdfTest();
    }

    @Test
    public void ruleWithRequiresEnsures() throws IOException {
        sdfTest();
    }

    @Test
    public void syntaxPriorities() throws IOException {
        outerOnlyTest();
    }

    @Test
    public void syntaxWithPriorities() throws IOException {
        outerOnlyTest();
    }

    @Test
    public void syntaxWithOR() throws IOException {
        outerOnlyTest();
    }

    @Test
    public void userList() throws IOException {
        sdfTest();
    }

    @Test
    public void userListNonEmpty() throws IOException {
        sdfTest();
    }

    @Test
    public void kapp() throws IOException {
        sdfTest();
    }

    @Test
    public void complex() throws IOException {
        sdfTest();
    }

    protected String convert(DefinitionWithContext defWithContext) {
        KILtoKORE kilToKore = new KILtoKORE(defWithContext.context);
        org.kframework.kore.outer.Definition koreDef = kilToKore.apply(defWithContext.definition);
        String koreDefString = koreDef.toString();
        return koreDefString;
    }

    protected String expectedFilePostfix() {
        return "-expected.k";
    }
}
