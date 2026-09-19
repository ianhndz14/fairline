package io.github.ianhndz14.fairline.stats;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Imports Premier League results from football-data.co.uk CSVs into the event table. */
@Component
public class ResultsImporter {

    private static final Logger log = LoggerFactory.getLogger(ResultsImporter.class);
    static final ZoneId UK = ZoneId.of("Europe/London");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** football-data short names mapped to Kalshi's names, which the app uses everywhere. Others match already. */
    static final Map<String, String> TEAM_NAMES = Map.of(
            "Man United", "Manchester United",
            "Man City", "Manchester City",
            "Nott'm Forest", "Nottingham Forest",
            "Leeds", "Leeds United",
            "Hull", "Hull City",
            "Ipswich", "Ipswich Town");

    public record MatchResult(String homeTeam, String awayTeam, Instant kickoff, int homeGoals, int awayGoals) {}

    private final EventRepository events;
    private final RestClient http;

    public ResultsImporter(EventRepository events, @Value("${fairline.results.base-url}") String baseUrl) {
        this.events = events;
        this.http = RestClient.create(baseUrl);
    }

    /** Imports the previous and current seasons. Safe to re-run: existing matches are only filled in. */
    @Scheduled(fixedDelayString = "${fairline.results.refresh-interval}")
    @Transactional
    public void importRecentSeasons() {
        LocalDate today = LocalDate.now(UK);
        int currentStart = today.getMonth().compareTo(Month.JULY) >= 0 ? today.getYear() : today.getYear() - 1;
        for (int start : List.of(currentStart - 1, currentStart)) {
            String season = "%02d%02d".formatted(start % 100, (start + 1) % 100);
            try {
                String csv = http.get()
                        .uri("/mmz4281/{season}/E0.csv", season)
                        .retrieve()
                        .body(String.class);
                if (csv == null) { // e.g. the site moved and answered with a redirect
                    log.warn("Empty response for season {} results", season);
                    continue;
                }
                log.info("Season {}: {} new or updated results", season, save(parse(csv)));
            } catch (RestClientException e) {
                // A new season's file doesn't exist until its first match; the next run picks it up.
                log.warn("Could not load results for season {}: {}", season, e.getMessage());
            }
        }
    }

    int save(List<MatchResult> results) {
        int changed = 0;
        for (MatchResult r : results) {
            // Match by teams within a day, since a Kalshi-created event may have a slightly different time.
            Optional<Event> existing = events.findFirstByHomeTeamAndAwayTeamAndKickoffBetween(
                    r.homeTeam(),
                    r.awayTeam(),
                    r.kickoff().minus(1, ChronoUnit.DAYS),
                    r.kickoff().plus(1, ChronoUnit.DAYS));
            if (existing.isEmpty()) {
                Event event = new Event(r.homeTeam(), r.awayTeam(), r.kickoff(), null);
                event.recordResult(r.homeGoals(), r.awayGoals());
                events.save(event);
                changed++;
            } else if (existing.get().getHomeGoals() == null) {
                existing.get().recordResult(r.homeGoals(), r.awayGoals());
                changed++;
            }
        }
        return changed;
    }

    /** Parses played matches from a football-data CSV; unplayed and blank rows are skipped. */
    static List<MatchResult> parse(String csv) {
        List<String> lines = csv.lines().filter(line -> !line.isBlank()).toList();
        // The file starts with a byte-order mark, which would otherwise stick to the first column name.
        List<String> header =
                List.of(lines.get(0).replaceFirst("^[^A-Za-z]+", "").split(","));
        int date = header.indexOf("Date"), time = header.indexOf("Time");
        int home = header.indexOf("HomeTeam"), away = header.indexOf("AwayTeam");
        int homeGoals = header.indexOf("FTHG"), awayGoals = header.indexOf("FTAG");
        int lastNeeded =
                IntStream.of(date, home, away, homeGoals, awayGoals).max().orElseThrow();

        List<MatchResult> results = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split(",", -1);
            if (f.length <= lastNeeded || f[homeGoals].isBlank()) continue;
            LocalTime kickoffTime = time >= 0 && !f[time].isBlank() ? LocalTime.parse(f[time]) : LocalTime.of(15, 0);
            Instant kickoff = LocalDate.parse(f[date], DATE)
                    .atTime(kickoffTime)
                    .atZone(UK)
                    .toInstant();
            results.add(new MatchResult(
                    canonical(f[home]),
                    canonical(f[away]),
                    kickoff,
                    Integer.parseInt(f[homeGoals]),
                    Integer.parseInt(f[awayGoals])));
        }
        return results;
    }

    static String canonical(String team) {
        return TEAM_NAMES.getOrDefault(team.trim(), team.trim());
    }
}
