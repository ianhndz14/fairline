package io.github.ianhndz14.fairline.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.stats.TeamStats.ExpectedGoals;
import io.github.ianhndz14.fairline.stats.TeamStats.League;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamStatsTest {

    private static final double EPS = 1e-6;

    private static Event played(String home, String away, int homeGoals, int awayGoals) {
        Event event = new Event(home, away, Instant.parse("2026-09-01T14:00:00Z"), null);
        event.recordResult(homeGoals, awayGoals);
        return event;
    }

    // Two matches: A 2-0 B and B 1-1 A. League averages: home (2+1)/2 = 1.5, away (0+1)/2 = 0.5.
    private final League league = TeamStats.compute(List.of(played("A", "B", 2, 0), played("B", "A", 1, 1)));

    @Test
    void computesLeagueAverages() {
        assertEquals(1.5, league.homeAvg(), EPS);
        assertEquals(0.5, league.awayAvg(), EPS);
    }

    @Test
    void expectedGoalsMatchHandCalculation() {
        // Each rate = (goals + 5 x league rate) / (games + 5):
        //   A home scored    (2 + 5 x 1.5) / 6 = 1.583333    B away conceded (2 + 5 x 1.5) / 6 = 1.583333
        //   B away scored    (0 + 5 x 0.5) / 6 = 0.416667    A home conceded (0 + 5 x 0.5) / 6 = 0.416667
        // home lambda = 1.583333 x 1.583333 / 1.5 = 1.671296; away lambda = 0.416667 x 0.416667 / 0.5 = 0.347222
        ExpectedGoals xg = league.expectedGoals("A", "B");
        assertEquals(1.671296, xg.home(), EPS);
        assertEquals(0.347222, xg.away(), EPS);
    }

    @Test
    void unknownTeamsGetLeagueAverages() {
        ExpectedGoals xg = league.expectedGoals("Newly Promoted", "Also New");
        assertEquals(1.5, xg.home(), EPS);
        assertEquals(0.5, xg.away(), EPS);
    }

    @Test
    void expectsNoGoalsWhenTheWindowHasNone() {
        // One 2-0 result: the league has never seen an away goal, so away lambda is 0, not a division by zero.
        League oneMatch = TeamStats.compute(List.of(played("A", "B", 2, 0)));
        assertEquals(0, oneMatch.expectedGoals("A", "B").away(), EPS);
        assertEquals(2.0, oneMatch.expectedGoals("A", "B").home(), EPS);
    }

    @Test
    void failsClearlyWithoutResults() {
        assertThrows(IllegalStateException.class, () -> TeamStats.compute(List.of()));
    }
}
