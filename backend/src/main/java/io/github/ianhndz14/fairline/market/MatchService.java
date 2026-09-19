package io.github.ianhndz14.fairline.market;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import io.github.ianhndz14.fairline.domain.Market;
import io.github.ianhndz14.fairline.domain.MarketRepository;
import io.github.ianhndz14.fairline.domain.ModelEstimate;
import io.github.ianhndz14.fairline.domain.ModelEstimateRepository;
import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.domain.PriceSnapshot;
import io.github.ianhndz14.fairline.domain.PriceSnapshotRepository;
import io.github.ianhndz14.fairline.engine.PoissonModel;
import io.github.ianhndz14.fairline.engine.PoissonModel.Probabilities;
import io.github.ianhndz14.fairline.market.EdgeDetector.Edge;
import io.github.ianhndz14.fairline.stats.TeamStats;
import io.github.ianhndz14.fairline.stats.TeamStats.ExpectedGoals;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases behind the REST API: calculator estimates, the opportunities list, and manual data entry. */
@Service
public class MatchService {

    public record Opportunity(long eventId, String homeTeam, String awayTeam, Instant kickoff, Outcome outcome,
                              double model, double market, double edge) {}

    /** {@code market} is null when there's no priced upcoming match between the two teams. */
    public record Estimate(String homeTeam, String awayTeam, double homeLambda, double awayLambda,
                           Probabilities probabilities, double[][] scoreGrid, Map<Outcome, Double> market) {}

    public record CreatedEvent(long id, String homeTeam, String awayTeam, Instant kickoff, double homeLambda,
                               double awayLambda, Probabilities probabilities) {}

    public record Price(@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal bid,
                        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal ask) {}

    private final TeamStats stats;
    private final EdgeDetector edgeDetector;
    private final EventRepository events;
    private final MarketRepository markets;
    private final PriceSnapshotRepository snapshots;
    private final ModelEstimateRepository estimates;

    public MatchService(TeamStats stats, EdgeDetector edgeDetector, EventRepository events,
                        MarketRepository markets, PriceSnapshotRepository snapshots,
                        ModelEstimateRepository estimates) {
        this.stats = stats;
        this.edgeDetector = edgeDetector;
        this.events = events;
        this.markets = markets;
        this.snapshots = snapshots;
        this.estimates = estimates;
    }

    /** Model output for any pairing. Either lambda can be overridden (the calculator's editable fields). */
    @Transactional(readOnly = true)
    public Estimate estimate(String homeTeam, String awayTeam, Double homeLambda, Double awayLambda) {
        ExpectedGoals xg = homeLambda != null && awayLambda != null
                ? new ExpectedGoals(homeLambda, awayLambda)
                : stats.expectedGoals(homeTeam, awayTeam);
        double home = homeLambda != null ? homeLambda : xg.home();
        double away = awayLambda != null ? awayLambda : xg.away();
        Map<Outcome, Double> market = events
                .findFirstByHomeTeamAndAwayTeamAndKickoffAfterOrderByKickoff(homeTeam, awayTeam, Instant.now())
                .map(edgeDetector::marketProbabilities)
                .filter(m -> m.size() == Outcome.values().length)
                .orElse(null);
        return new Estimate(homeTeam, awayTeam, home, away, PoissonModel.matchProbabilities(home, away),
                PoissonModel.scoreGrid(home, away), market);
    }

    /** Upcoming outcomes where the latest model estimate beats the market by at least minEdge, biggest first. */
    @Transactional(readOnly = true)
    public List<Opportunity> opportunities(double minEdge, Instant now) {
        List<Opportunity> result = new ArrayList<>();
        for (Event event : events.findByKickoffAfterOrderByKickoff(now)) {
            estimates.findFirstByEventOrderByCreatedAtDesc(event).ifPresent(estimate ->
                    edgeDetector.edges(event, estimate).stream()
                            .filter(e -> e.edge() >= minEdge)
                            .forEach(e -> result.add(toOpportunity(e))));
        }
        result.sort(Comparator.comparingDouble(Opportunity::edge).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<Instant> lastPriceUpdate() {
        return snapshots.findFirstByOrderByCapturedAtDesc().map(PriceSnapshot::getCapturedAt);
    }

    /** Plan B: add a match Kalshi doesn't list. */
    @Transactional
    public CreatedEvent createEvent(String homeTeam, String awayTeam, Instant kickoff) {
        if (homeTeam.equalsIgnoreCase(awayTeam)) {
            throw new IllegalArgumentException("A team can't play itself");
        }
        Event event = events.save(new Event(homeTeam, awayTeam, kickoff, null));
        ModelEstimate estimate = edgeDetector.refreshEstimate(event, Instant.now());
        return new CreatedEvent(event.getId(), homeTeam, awayTeam, kickoff, estimate.getHomeLambda(),
                estimate.getAwayLambda(), new Probabilities(estimate.probabilityOf(Outcome.HOME),
                estimate.probabilityOf(Outcome.DRAW), estimate.probabilityOf(Outcome.AWAY)));
    }

    /** Plan B: enter all three prices by hand; returns any opportunities they create. */
    @Transactional
    public List<Opportunity> recordPrices(long eventId, Map<Outcome, Price> prices, Instant now) {
        if (prices.size() != Outcome.values().length) {
            throw new IllegalArgumentException("Prices are required for HOME, DRAW and AWAY");
        }
        prices.forEach((outcome, p) -> {
            if (p.bid().compareTo(p.ask()) > 0) {
                throw new IllegalArgumentException(outcome + " bid must not exceed ask");
            }
        });
        Event event = events.findById(eventId).orElseThrow(() -> new NoSuchElementException("No event " + eventId));
        prices.forEach((outcome, p) -> {
            Market market = markets.findByEventAndOutcome(event, outcome)
                    .orElseGet(() -> markets.save(new Market(event, outcome, null)));
            snapshots.save(new PriceSnapshot(market, now, p.bid(), p.ask()));
        });
        return edgeDetector.evaluate(event, now).stream().map(MatchService::toOpportunity).toList();
    }

    private static Opportunity toOpportunity(Edge e) {
        Event event = e.event();
        return new Opportunity(event.getId(), event.getHomeTeam(), event.getAwayTeam(), event.getKickoff(),
                e.outcome(), e.model(), e.market(), e.edge());
    }
}
