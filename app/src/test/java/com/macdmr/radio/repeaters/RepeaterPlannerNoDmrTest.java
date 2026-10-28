package com.macdmr.radio.repeaters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.macdmr.radio.repeaters.RepeaterModels.Repeater;
import com.macdmr.radio.repeaters.RepeaterModels.Talkgroup;
import com.macdmr.radio.repeaters.RepeaterPlanner.Plan;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Test;

/** A radio without DMR (the W6): Nearby repeaters adds a DMR + FM repeater as its FM channel only, a DMR-only one not at all. */
public class RepeaterPlannerNoDmrTest {
    private static Repeater rep(boolean fm, boolean dmr) {
        Repeater r = new Repeater();
        r.callsign = "N0CALL"; r.city = "Testville"; r.rxHz = 444_100_000L; r.txHz = 449_100_000L; r.fm = fm; r.dmr = dmr; r.cc = 1;
        if (dmr) r.talkgroups.add(new Talkgroup(91, 1, "Worldwide", "static"));
        return r;
    }

    @Test public void dmrAndFmRepeaterBecomesOneFmChannel() {
        Plan p = RepeaterPlanner.plan(rep(true, true), new HashSet<>(), new HashMap<>(), false);
        assertEquals(1, p.channels.size());
        assertEquals(1, p.channels.get(0).type);
    }

    @Test public void dmrOnlyRepeaterGivesNothing() {
        Plan p = RepeaterPlanner.plan(rep(false, true), new HashSet<>(), new HashMap<>(), false);
        assertTrue(p.channels.isEmpty());
        assertTrue(p.warnings.get(0).contains("no DMR"));
    }

    @Test public void dmrRadioStillPlansDmrChannels() {
        Plan p = RepeaterPlanner.plan(rep(true, true), new HashSet<>(), new HashMap<>(), true);
        assertTrue(p.channels.stream().anyMatch(c -> c.type == 0));
    }
}
