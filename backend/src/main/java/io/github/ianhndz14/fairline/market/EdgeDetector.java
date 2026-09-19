package io.github.ianhndz14.fairline.market;

import io.github.ianhndz14.fairline.domain.EdgeLog;
import io.github.ianhndz14.fairline.domain.EdgeLogRepository;
import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.Market;
import io.github.ianhndz14.fairline.domain.MarketRepository;
import io.github.ianhndz14.fairline.domain.ModelEstimate;
import io.github.ianhndz14.fairline.domain.ModelEstimateRepository;
import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.domain.PriceSnapshot;
import io.github.ianhndz14.fairline.domain.PriceSnapshotRepository;
import io.github.ianhndz14.fairline.engine.PoissonModel;
import io.github.ianhndz14.fairline.stats.TeamStats;
import io.github.ianhndz14.fairline.stats.TeamStats.ExpectedGoals;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Compares the Poisson model with normalized market prices and logs outcomes the market underprices. */
@Service
public class EdgeDetector {

    /** Edge = model probability - normalized market probability. Positive means the market underprices it. */
    public record Edge(Event event, Outcome outcome, double model, double market) {
        public double edge() {
            return model - market;
        }
    }

    private final TeamStats stats;
    private final ModelEstimateRepository estimates;
    private final MarketRepository markets;
    private final PriceSnapshotRepository snapshots;
    private final EdgeLogRepository edgeLogs;
    private final double threshold;

    public EdgeDetector(
            TeamStats stats,
            ModelEstimateRepository estimates,
            MarketRepository markets,
            PriceSnapshotRepository snapshots,
            EdgeLogRepository edgeLogs,
            @Value("${fairline.edge.threshold}") double threshold) {
        this.stats = stats;
        this.estimates = estimates;
        this.markets = markets;
        this.snapshots = snapshots;
        this.edgeLogs = edgeLogs;
        this.threshold = threshold;
    }

    public double threshold() {
        return threshold;
    }

    /** Refreshes the event's model estimate, then logs and returns every outcome at or above the threshold. */
    public List<Edge> evaluate(Event event, Instant now) {
        ModelEstimate estimate = refreshEstimate(event, now);
        List<Edge> flagged = edges(event, estimate).stream()
                .filter(e -> e.edge() >= threshold)
                .toList();
        for (Edge e : flagged) {
            Market market = markets.findByEventAndOutcome(event, e.outcome()).orElseThrow();
            edgeLogs.save(new EdgeLog(market, estimate, now, e.market()));
        }
        return flagged;
    }

    /** Saves a new estimate only when the expected goals changed (i.e. new results came in). */
    ModelEstimate refreshEstimate(Event event, Instant now) {
        ExpectedGoals xg = stats.expectedGoals(event.getHomeTeam(), event.getAwayTeam());
        return estimates
                .findFirstByEventOrderByCreatedAtDesc(event)
                .filter(latest -> latest.getHomeLambda() == xg.home() && latest.getAwayLambda() == xg.away())
                .orElseGet(() -> estimates.save(new ModelEstimate(
                        event, now, xg.home(), xg.away(), PoissonModel.matchProbabilities(xg.home(), xg.away()))));
    }

    /** Model vs market for all three outcomes; empty until every outcome has a price. */
    public List<Edge> edges(Event event, ModelEstimate estimate) {
        Map<Outcome, Double> market = marketProbabilities(event);
        if (market.size() < Outcome.values().length) {
            return List.of();
        }
        return Arrays.stream(Outcome.values())
                .map(o -> new Edge(event, o, estimate.probabilityOf(o), market.get(o)))
                .toList();
    }

    /** Normalized market probabilities from each outcome's latest snapshot. */
    public Map<Outcome, Double> marketProbabilities(Event event) {
        Map<Outcome, PriceSnapshot> latest = new EnumMap<>(Outcome.class);
        for (Market market : markets.findByEvent(event)) {
            snapshots.findFirstByMarketOrderByCapturedAtDesc(market).ifPresent(s -> latest.put(market.getOutcome(), s));
        }
        return normalizedMidPrices(latest);
    }

    /**
     * Mid prices scaled to sum to 1. Kalshi's three prices add up to slightly more than 1 (the market's
     * margin), so they have to be normalized before comparing them with the model's probabilities.
     */
    public static Map<Outcome, Double> normalizedMidPrices(Map<Outcome, PriceSnapshot> latest) {
        Map<Outcome, Double> mid = new EnumMap<>(Outcome.class);
        latest.forEach((o, s) -> mid.put(o, s.getYesBid().add(s.getYesAsk()).doubleValue() / 2));
        double total = mid.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return Map.of();
        }
        mid.replaceAll((o, p) -> p / total);
        return mid;
    }
}
