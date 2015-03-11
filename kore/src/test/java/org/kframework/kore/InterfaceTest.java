// Copyright (c) 2014-2015 K Team. All Rights Reserved.

package org.kframework.kore;

import org.junit.Test;
import org.kframework.builtin.Sorts;

import static org.junit.Assert.*;
import static org.kframework.kore.KORE.*;

public class InterfaceTest {

    @Test
    public void example() {
        // Creating "A + 0 => A" programmatically

        KRewrite k = KRewrite(
                KApply(KLabel("_+_"), KList(KVariable("A"), KToken(Sort("Int"), "0"))),
                KVariable("A"));

        // Navigating it
        KLabel theLabel = ((KApply) k.left()).klabel();
        theLabel.name();
    }

    @Test
    public void kListIsAssociative() {
        // assertEquals(KList(KToken(Sorts.Int(), "1"), KToken(Sorts.Int(), "2")), KList(KToken(Sorts.Int(), "1"), KList(KToken(Sorts.Int(), "2"))));
    }

    @Test
    public void manipulatingKList() {
        KList l = KList(KToken(Sorts.Int(), "1"), KToken(Sorts.Int(), "2")).stream().map(x -> KToken(Sorts.Int(), "3")).collect(toKList());
        assertEquals(KList(KToken(Sorts.Int(), "3"), KToken(Sorts.Int(), "3")), l);
    }

    @Test
    public void kSeqIsAssociative() {
        assertEquals(KSequence(KToken(Sorts.Int(), "1"), KToken(Sorts.Int(), "2")), KSequence(KToken(Sorts.Int(), "1"), KSequence(KToken(Sorts.Int(), "2"))));
    }

//    @Test
//    public void manipulatingKSeq() {
//        KSequence l = stream(KSequence(KToken(Sorts.Int(), "1"), KToken(Sorts.Int(), "2"))).map(x -> KToken(Sorts.Int(), "3")).collect(toKSequence());
//        assertEquals(KSequence(KToken(Sorts.Int(), "3"), KToken(Sorts.Int(), "3")), l);
//    }
}
