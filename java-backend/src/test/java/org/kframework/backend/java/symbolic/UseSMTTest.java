// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.kil.BuiltinMap;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.utils.options.SMTOptions;
import org.kframework.utils.options.SMTSolver;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.HashMultimap;

@RunWith(MockitoJUnitRunner.class)
public class UseSMTTest {

    @Mock
    TermContext tc;

    @Mock
    Definition definition;

    @Mock
    SMTOperations constraintOps;


    @Before
    public void setUp() {
        when(tc.definition()).thenReturn(definition);
        when(definition.functionRules()).thenReturn(HashMultimap.<KLabelConstant, Rule>create());
        when(definition.kLabels()).thenReturn(new HashSet<>());
        when(tc.global()).thenReturn(new GlobalContext(null, null, constraintOps, null, null));
    }

    @Test
    public void testGetModel() {
        System.err.println(System.getProperty("java.library.path"));
        BuiltinMap.Builder builder = new BuiltinMap.Builder(tc);
        SMTOptions options = new SMTOptions();
        options.smt = SMTSolver.Z3;
        assertEquals(builder.build(), new UseSMT(options).checkSat(BoolToken.TRUE, tc));
    }
}
