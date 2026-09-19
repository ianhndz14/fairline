package io.github.ianhndz14.fairline.market;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import io.github.ianhndz14.fairline.domain.Market;
import io.github.ianhndz14.fairline.domain.MarketRepository;
import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.domain.PriceSnapshot;
import io.github.ianhndz14.fairline.domain.PriceSnapshotRepository;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiEvent;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiMarket;
import io.github.ianhndz14.fairline.stats.TeamStats;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Pulls open Kalshi EPL markets and stores a timestamped price snapshot for each one. */
@Component
public class PriceIngestion {

    private static final Logger log = LoggerFactory.getLogger(PriceIngestion.class);

    /** Kalshi's occurrence_datetime is 3 hours after kickoff (checked against football-data kickoff times). */
    static final Duration KALSHI_TIME_OFFSET = Duration.ofHours(3);

    private final KalshiClient kalshi;
    private final EventRepository events;
    private final MarketRepository markets;
    private final PriceSnapshotRepository snapshots;
    private final EdgeDetector edgeDetector;

    public PriceIngestion(KalshiClient kalshi, EventRepository events, MarketRepository markets,
                          PriceSnapshotRepository snapshots, EdgeDetector edgeDetector) {
        this.kalshi = kalshi;
        this.events = events;
        this.markets = markets;
        this.snapshots = snapshots;
        this.edgeDetector = edgeDetector;
    }

    /** Stores fresh prices, then compares every priced match against the model. */
    @Scheduled(fixedDelayString = "${fairline.kalshi.refresh-interval}", initialDelayString = "PT30S")
    @Transactional
    public void refresh() {
        Instant now = Instant.now();
        List<Event> priced = ingest(kalshi.openEplGames(), now);
        try {
            int flagged = priced.stream().mapToInt(e -> edgeDetector.evaluate(e, now).size()).sum();
            log.info("Flagged {} opportunities at threshold {}", flagged, edgeDetector.threshold());
        } catch (TeamStats.NoResultsException e) {
            log.warn("Skipping edge detection: {}", e.getMessage());
        }
    }

    /** Stores prices for matches that haven't kicked off yet; returns those events. */
    List<Event> ingest(List<KalshiEvent> open, Instant now) {
        List<Event> priced = new ArrayList<>();
        for (KalshiEvent k : open) {
            String[] teams = k.title() == null ? new String[0] : k.title().split(" vs ");
            if (teams.length != 2 || k.markets() == null || k.markets().isEmpty()
                    || k.markets().get(0).occurrence() == null) {
                log.warn("Skipping unexpected Kalshi event {} ({})", k.eventTicker(), k.title());
                continue;
            }
            Instant kickoff = k.markets().get(0).occurrence().minus(KALSHI_TIME_OFFSET);
            if (!kickoff.isAfter(now)) {
                continue; // in-play prices react to goals; comparing them with a pre-match model is meaningless
            }
            Event event = findOrCreate(k, teams[0].trim(), teams[1].trim(), kickoff);
            for (KalshiMarket m : k.markets()) {
                Outcome outcome = outcomeOf(m.yesSubTitle(), event);
                if (outcome == null) {
                    log.warn("Kalshi market {} names unknown team '{}'", m.ticker(), m.yesSubTitle());
                    continue;
                }
                Market market = markets.findByEventAndOutcome(event, outcome)
                        .orElseGet(() -> markets.save(new Market(event, outcome, m.ticker())));
                if (m.yesBid() != null && m.yesAsk() != null && m.yesBid().compareTo(m.yesAsk()) <= 0) {
                    snapshots.save(new PriceSnapshot(market, now, m.yesBid(), m.yesAsk()));
                }
            }
            priced.add(event);
        }
        log.info("Stored Kalshi prices for {} upcoming matches", priced.size());
        return priced;
    }

    private Event findOrCreate(KalshiEvent k, String homeTeam, String awayTeam, Instant kickoff) {
        return events.findByKalshiEventTicker(k.eventTicker())
                .or(() -> events.findFirstByHomeTeamAndAwayTeamAndKickoffBetween(homeTeam, awayTeam,
                                kickoff.minus(1, ChronoUnit.DAYS), kickoff.plus(1, ChronoUnit.DAYS))
                        .map(manual -> { manual.linkKalshi(k.eventTicker()); return manual; }))
                .orElseGet(() -> events.save(new Event(homeTeam, awayTeam, kickoff, k.eventTicker())));
    }

    static Outcome outcomeOf(String yesSubTitle, Event event) {
        if ("Tie".equals(yesSubTitle)) return Outcome.DRAW;
        if (event.getHomeTeam().equals(yesSubTitle)) return Outcome.HOME;
        if (event.getAwayTeam().equals(yesSubTitle)) return Outcome.AWAY;
        return null;
    }
}
