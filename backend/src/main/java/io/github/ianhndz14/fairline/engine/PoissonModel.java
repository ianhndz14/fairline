package io.github.ianhndz14.fairline.engine;

/**
 * Independent-Poisson match model: each team's goal count follows a Poisson
 * distribution with its own expected-goals rate (lambda). Pure Java, no framework.
 */
public final class PoissonModel {

    // Scorelines above 10 goals are dropped and the grid renormalized. For realistic
    // soccer rates (lambda <= 4) the dropped mass is < 1e-4; raise this for high-scoring sports.
    public static final int MAX_GOALS = 10;

    public record Probabilities(double homeWin, double draw, double awayWin) {}

    private PoissonModel() {}

    /** Expected goals for one side: its scoring average x opponent's conceding average / league average. */
    public static double expectedGoals(double scoredAvg, double opponentConcededAvg, double leagueAvg) {
        requireNonNegative(scoredAvg, "scoredAvg");
        requireNonNegative(opponentConcededAvg, "opponentConcededAvg");
        if (!(leagueAvg > 0) || Double.isInfinite(leagueAvg)) {
            throw new IllegalArgumentException("leagueAvg must be positive, got " + leagueAvg);
        }
        return scoredAvg * opponentConcededAvg / leagueAvg;
    }

    /** P(goals = k) for k = 0..MAX_GOALS, using P(k) = P(k-1) * lambda / k to avoid computing factorials. */
    static double[] goalDistribution(double lambda) {
        requireNonNegative(lambda, "lambda");
        double[] p = new double[MAX_GOALS + 1];
        p[0] = Math.exp(-lambda);
        for (int k = 1; k <= MAX_GOALS; k++) {
            p[k] = p[k - 1] * lambda / k;
        }
        return p;
    }

    /** grid[h][a] = P(home scores h, away scores a), normalized so all cells sum to 1. */
    public static double[][] scoreGrid(double homeLambda, double awayLambda) {
        double[] home = goalDistribution(homeLambda);
        double[] away = goalDistribution(awayLambda);
        double[][] grid = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        double total = 0;
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                grid[h][a] = home[h] * away[a];
                total += grid[h][a];
            }
        }
        for (double[] row : grid) {
            for (int a = 0; a < row.length; a++) {
                row[a] /= total;
            }
        }
        return grid;
    }

    /** 1X2 probabilities: sums of the grid below, on and above the diagonal. */
    public static Probabilities matchProbabilities(double homeLambda, double awayLambda) {
        double[][] grid = scoreGrid(homeLambda, awayLambda);
        double homeWin = 0, draw = 0, awayWin = 0;
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                if (h > a) homeWin += grid[h][a];
                else if (h == a) draw += grid[h][a];
                else awayWin += grid[h][a];
            }
        }
        return new Probabilities(homeWin, draw, awayWin);
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a finite number >= 0, got " + value);
        }
    }
}
