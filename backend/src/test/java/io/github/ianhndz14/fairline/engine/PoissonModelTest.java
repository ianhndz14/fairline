package io.github.ianhndz14.fairline.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PoissonModelTest {

    private static final double EPS = 1e-6;

    @Test
    void goalDistributionMatchesHandCalculatedValues() {
        // P(0; 1) = e^-1
        assertEquals(0.367879, PoissonModel.goalDistribution(1.0)[0], EPS);
        // P(2; 1.5) = e^-1.5 * 1.5^2 / 2! = 0.223130 * 1.125
        assertEquals(0.251021, PoissonModel.goalDistribution(1.5)[2], EPS);
    }

    @Test
    void scoreGridSumsToOne() {
        double total = Arrays.stream(PoissonModel.scoreGrid(2.7, 0.4))
                .flatMapToDouble(Arrays::stream)
                .sum();
        assertEquals(1.0, total, 1e-12);
    }

    @Test
    void outcomesSumToOne() {
        var p = PoissonModel.matchProbabilities(1.6, 1.1);
        assertEquals(1.0, p.homeWin() + p.draw() + p.awayWin(), 1e-12);
    }

    @Test
    void evenlyMatchedTeamsMatchHandCalculation() {
        // lambda = 1 each: P(draw) = sum_k P(k)^2 = e^-2 * sum_k 1/(k!)^2 = 0.135335 * 2.279585
        var p = PoissonModel.matchProbabilities(1.0, 1.0);
        assertEquals(0.308508, p.draw(), EPS);
        assertEquals(0.345746, p.homeWin(), EPS);
        assertEquals(p.homeWin(), p.awayWin(), 1e-12);
    }

    @Test
    void strongerSideIsFavoured() {
        var p = PoissonModel.matchProbabilities(2.0, 0.8);
        assertTrue(p.homeWin() > p.awayWin());
    }

    @Test
    void zeroLambdaMeansNoGoals() {
        // Both sides certain to score 0 -> certain 0-0 draw.
        assertEquals(1.0, PoissonModel.matchProbabilities(0, 0).draw(), 1e-12);
    }

    @Test
    void expectedGoalsFromAverages() {
        // Scores 2.0/game, opponent concedes 1.5/game, league average 1.5 -> 2.0 * 1.5 / 1.5
        assertEquals(2.0, PoissonModel.expectedGoals(2.0, 1.5, 1.5), 1e-12);
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> PoissonModel.matchProbabilities(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> PoissonModel.matchProbabilities(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> PoissonModel.expectedGoals(1, 1, 0));
    }
}
