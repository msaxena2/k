// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.krun.api;

import java.util.Map;

/**
 * Transition unit in a KRun computation
 */
public class KRunTransitionUnit extends KRunUnit{

    Map<KilTermContainer, KilTermContainer> substMap;

    public KRunTransitionUnit(KilTermContainer transitionRule) {
        super(transitionRule);
    }
}
