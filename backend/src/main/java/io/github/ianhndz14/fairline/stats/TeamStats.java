package io.github.ianhndz14.fairline.stats;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import io.github.ianhndz14.fairline.engine.PoissonModel;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Per-team home/away scoring rates from the last year of results, and the expected goals they imply. */
@Service
public class TeamStats {

    static final Duration WINDOW = Duration.ofDays(365);

    /** Each team's record is blended with this many league-average matches, so 2-3 early games don't dominate. */
    static final double PRIOR_MATCHES = 5;

    /**
     * Teams without a match in this long are left out of the team list. It covers the ~12-week summer break,
     * so relegated teams linger for about a week each August before dropping off.
     */
    static final Duration ACTIVE = Duration.ofDays(90);

    public record TeamStrength(
            String team,
            int matches,
            Instant lastMatch,
            double homeScored,
            double homeConceded,
            double awayScored,
            double awayConceded) {}

    public record ExpectedGoals(double home, double away) {}

    /** No results have been imported yet (e.g. first startup), so there is nothing to model from. */
    public static class NoResultsException extends IllegalStateException {
        @Serial
        private static final long serialVersionUID = 1L;

        NoResultsException() {
            super("No match results imported yet");
        }
    }

    record League(double homeAvg, double awayAvg, Map<String, TeamStrength> teams) {

        /** A team with no results gets exactly the league averages. */
        TeamStrength team(String name) {
            return teams.getOrDefault(name, new TeamStrength(name, 0, null, homeAvg, awayAvg, awayAvg, homeAvg));
        }

        ExpectedGoals expectedGoals(String homeTeam, String awayTeam) {
            TeamStrength home = team(homeTeam), away = team(awayTeam);
            return new ExpectedGoals(
                    PoissonModel.expectedGoals(home.homeScored(), away.awayConceded(), homeAvg),
                    PoissonModel.expectedGoals(away.awayScored(), home.homeConceded(), awayAvg));
        }
    }

    private final EventRepository events;

    public TeamStats(EventRepository events) {
        this.events = events;
    }

    public List<TeamStrength> activeTeams() {
        Instant cutoff = Instant.now().minus(ACTIVE);
        return league().teams().values().stream()
                .filter(t -> t.lastMatch().isAfter(cutoff))
                .toList();
    }

    public ExpectedGoals expectedGoals(String homeTeam, String awayTeam) {
        return league().expectedGoals(homeTeam, awayTeam);
    }

    private League league() {
        return compute(
                events.findByHomeGoalsNotNullAndKickoffAfter(Instant.now().minus(WINDOW)));
    }

    static League compute(List<Event> played) {
        if (played.isEmpty()) {
            throw new NoResultsException();
        }
        double homeAvg = played.stream().mapToInt(Event::getHomeGoals).average().orElseThrow();
        double awayAvg = played.stream().mapToInt(Event::getAwayGoals).average().orElseThrow();

        Map<String, Tally> tallies = new HashMap<>();
        for (Event e : played) {
            tallies.computeIfAbsent(e.getHomeTeam(), k -> new Tally())
                    .home(e.getHomeGoals(), e.getAwayGoals(), e.getKickoff());
            tallies.computeIfAbsent(e.getAwayTeam(), k -> new Tally())
                    .away(e.getAwayGoals(), e.getHomeGoals(), e.getKickoff());
        }
        Map<String, TeamStrength> teams = new TreeMap<>(); // sorted by team name
        tallies.forEach((name, t) -> teams.put(
                name,
                new TeamStrength(
                        name,
                        t.homeGames + t.awayGames,
                        t.lastMatch,
                        shrink(t.homeFor, t.homeGames, homeAvg),
                        shrink(t.homeAgainst, t.homeGames, awayAvg),
                        shrink(t.awayFor, t.awayGames, awayAvg),
                        shrink(t.awayAgainst, t.awayGames, homeAvg))));
        return new League(homeAvg, awayAvg, teams);
    }

    static double shrink(int goals, int games, double leagueRate) {
        return (goals + PRIOR_MATCHES * leagueRate) / (games + PRIOR_MATCHES);
    }

    private static final class Tally {
        int homeGames, homeFor, homeAgainst, awayGames, awayFor, awayAgainst;
        Instant lastMatch = Instant.MIN;

        void home(int scored, int conceded, Instant kickoff) {
            homeGames++;
            homeFor += scored;
            homeAgainst += conceded;
            seen(kickoff);
        }

        void away(int scored, int conceded, Instant kickoff) {
            awayGames++;
            awayFor += scored;
            awayAgainst += conceded;
            seen(kickoff);
        }

        private void seen(Instant kickoff) {
            if (kickoff.isAfter(lastMatch)) lastMatch = kickoff;
        }
    }
}
