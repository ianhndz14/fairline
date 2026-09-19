package io.github.ianhndz14.fairline.market;

import io.github.ianhndz14.fairline.domain.EdgeLog;
import io.github.ianhndz14.fairline.domain.EdgeLogRepository;
import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import io.github.ianhndz14.fairline.domain.ModelEstimate;
import io.github.ianhndz14.fairline.domain.ModelEstimateRepository;
import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.domain.PriceSnapshot;
import io.github.ianhndz14.fairline.domain.PriceSnapshotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** How model and market moved over time, and how flagged opportunities turned out. */
@Service
public class HistoryService {

    static final Duration RECENT = Duration.ofDays(30);

    public record EventSummary(
            long id, String homeTeam, String awayTeam, Instant kickoff, Integer homeGoals, Integer awayGoals) {
        static EventSummary of(Event e) {
            return new EventSummary(
                    e.getId(), e.getHomeTeam(), e.getAwayTeam(), e.getKickoff(), e.getHomeGoals(), e.getAwayGoals());
        }
    }

    /** Normalized market and the model estimate in force at one price capture. */
    public record HistoryPoint(Instant time, Map<Outcome, Double> model, Map<Outcome, Double> market) {}

    public record History(EventSummary event, List<HistoryPoint> points) {}

    /** {@code hit} is null until the match result is known. */
    public record TrackedEdge(
            EventSummary event,
            Outcome outcome,
            Instant detectedAt,
            double model,
            double market,
            double edge,
            Boolean hit) {}

    /** Averages are over settled edges; comparing hitRate with averageMarket shows whether the edges were real. */
    public record TrackRecord(
            int settled,
            int hits,
            Double hitRate,
            Double averageModel,
            Double averageMarket,
            List<TrackedEdge> edges) {}

    private final EventRepository events;
    private final PriceSnapshotRepository snapshots;
    private final ModelEstimateRepository estimates;
    private final EdgeLogRepository edgeLogs;

    public HistoryService(
            EventRepository events,
            PriceSnapshotRepository snapshots,
            ModelEstimateRepository estimates,
            EdgeLogRepository edgeLogs) {
        this.events = events;
        this.snapshots = snapshots;
        this.estimates = estimates;
        this.edgeLogs = edgeLogs;
    }

    /** Matches with market prices, from the last 30 days onwards. */
    @Transactional(readOnly = true)
    public List<EventSummary> pricedEvents(Instant now) {
        return events.findPricedSince(now.minus(RECENT)).stream()
                .map(EventSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public History history(long eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new NoSuchElementException("No event " + eventId));
        List<ModelEstimate> modelHistory = estimates.findByEventOrderByCreatedAt(event);

        // Each ingestion run stores all three outcomes with the same timestamp, so group by capture time.
        Map<Instant, Map<Outcome, PriceSnapshot>> byTime = new TreeMap<>();
        for (PriceSnapshot s : snapshots.findByEvent(event)) {
            byTime.computeIfAbsent(s.getCapturedAt(), t -> new EnumMap<>(Outcome.class))
                    .put(s.getMarket().getOutcome(), s);
        }

        List<HistoryPoint> points = new ArrayList<>();
        byTime.forEach((time, prices) -> {
            Map<Outcome, Double> market = EdgeDetector.normalizedMidPrices(prices);
            ModelEstimate estimate = estimateAt(modelHistory, time);
            if (market.size() == Outcome.values().length && estimate != null) {
                points.add(new HistoryPoint(time, probabilities(estimate), market));
            }
        });
        return new History(EventSummary.of(event), points);
    }

    /**
     * Every outcome flagged for a match that has kicked off. The job logs an edge on every run, so each
     * (match, outcome) is judged once, by its closing edge: the last one logged before kickoff.
     */
    @Transactional(readOnly = true)
    public TrackRecord trackRecord(Instant now) {
        Map<Long, EdgeLog> closing = new HashMap<>(); // keyed by market = one (match, outcome)
        for (EdgeLog log : edgeLogs.findForMatchesBefore(now)) {
            if (log.getDetectedAt().isBefore(log.getMarket().getEvent().getKickoff())) {
                closing.merge(
                        log.getMarket().getId(),
                        log,
                        (a, b) -> a.getDetectedAt().isAfter(b.getDetectedAt()) ? a : b);
            }
        }

        List<TrackedEdge> tracked = closing.values().stream()
                .map(HistoryService::track)
                .sorted(Comparator.comparing((TrackedEdge t) -> t.event().kickoff())
                        .reversed())
                .toList();
        List<TrackedEdge> settled =
                tracked.stream().filter(t -> t.hit() != null).toList();
        int hits = (int) settled.stream().filter(TrackedEdge::hit).count();
        return new TrackRecord(
                settled.size(),
                hits,
                settled.isEmpty() ? null : (double) hits / settled.size(),
                average(settled, TrackedEdge::model),
                average(settled, TrackedEdge::market),
                tracked);
    }

    private static TrackedEdge track(EdgeLog log) {
        Event event = log.getMarket().getEvent();
        Outcome outcome = log.getMarket().getOutcome();
        Boolean hit = event.result() == null ? null : event.result() == outcome;
        return new TrackedEdge(
                EventSummary.of(event),
                outcome,
                log.getDetectedAt(),
                log.getModelProb(),
                log.getMarketProb(),
                log.getEdge(),
                hit);
    }

    /** The latest estimate made at or before {@code time}; the first one if all are later. */
    private static ModelEstimate estimateAt(List<ModelEstimate> modelHistory, Instant time) {
        ModelEstimate current = modelHistory.isEmpty() ? null : modelHistory.get(0);
        for (ModelEstimate e : modelHistory) {
            if (e.getCreatedAt().isAfter(time)) break;
            current = e;
        }
        return current;
    }

    private static Map<Outcome, Double> probabilities(ModelEstimate estimate) {
        Map<Outcome, Double> p = new EnumMap<>(Outcome.class);
        for (Outcome o : Outcome.values()) p.put(o, estimate.probabilityOf(o));
        return p;
    }

    private static Double average(List<TrackedEdge> edges, ToDoubleFunction<TrackedEdge> value) {
        return edges.isEmpty()
                ? null
                : edges.stream().mapToDouble(value).average().orElseThrow();
    }
}
