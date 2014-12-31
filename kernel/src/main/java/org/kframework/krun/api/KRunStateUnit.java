// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.krun.api;

import com.google.inject.Singleton;

/**
 * Represents a state in KRun
 */
public class KRunStateUnit extends KRunUnit{

    private int stateId;

    public KRunStateUnit(KilContainer stateContainer, KRunState.Counter counter) {
        super(stateContainer);
        stateId = counter.getNextState();
        counter.setNextState(counter.getNextState() + 1);
    }

    public int getStateId() {
        return stateId;
    }

    public void setStateId(int stateId) {
        this.stateId = stateId;
    }



    @Override
    public boolean equals(Object o) {
        if (!(o instanceof KRunStateUnit)) return false;
        KRunStateUnit s = (KRunStateUnit)o;
        /*jung uses intensively equals while drawing graphs
          use SemanticEquals since it caches results
        */
        return SemanticEqual.checkEquality(getKilTerm(), s.getKilTerm());
    }

    @Override
    public int hashCode() {
        return getKilTerm().hashCode();
    }


}
