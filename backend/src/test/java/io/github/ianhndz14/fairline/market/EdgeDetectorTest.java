package io.github.ianhndz14.fairline.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ianhndz14.fairline.domain.EdgeLogRepository;
import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import io.github.ianhndz14.fairline.domain.ModelEstimate;
import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.domain.PriceSnapshot;
import io.github.ianhndz14.fairline.market.EdgeDetector.Edge;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiEvent;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiMarket;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"fairline.scheduling.enabled=false", "fairline.edge.threshold=0.05"})
@Transactional
class EdgeDetectorTest {

    private static final Instant NOW = Instant.now();

    @Autowired EdgeDetector detector;
    @Autowired PriceIngestion ingestion;
    @Autowired EventRepository events;
    @Autowired EdgeLogRepository edgeLogs;

    private static PriceSnapshot price(String bid, String ask) {
        return new PriceSnapshot(null, NOW, new BigDecimal(bid), new BigDecimal(ask));
    }

    @Test
    void normalizesMidPricesToSumToOne() {
        // Real Fulham vs Man United prices: mids 0.275 + 0.255 + 0.475 = 1.005, so each is divided by 1.005.
        Map<Outcome, Double> market = EdgeDetector.normalizedMidPrices(Map.of(
                Outcome.HOME, price("0.27", "0.28"),
                Outcome.DRAW, price("0.25", "0.26"),
                Outcome.AWAY, price("0.47", "0.48")));
        assertEquals(0.273632, market.get(Outcome.HOME), 1e-6);
        assertEquals(0.253731, market.get(Outcome.DRAW), 1e-6);
        assertEquals(0.472637, market.get(Outcome.AWAY), 1e-6);
    }

    @Test
    void flagsOnlyOutcomesAboveThreshold() {
        // Guarantees team stats exist even on an empty database (e.g. in CI).
        Event played = new Event("Test United", "Test City", NOW.minus(Duration.ofDays(10)), null);
        played.recordResult(1, 1);
        events.save(played);

        // The market prices the home side at ~1.5%, far below any sensible model estimate,
        // and the draw and away sides above it, so only HOME should clear the 5-point threshold.
        Instant occurrence = NOW.plus(Duration.ofDays(2));
        ingestion.ingest(List.of(new KalshiEvent("TEST-HOMAWA", "Home FC vs Away FC", List.of(
                new KalshiMarket("TEST-HOMAWA-HOM", "Home FC", new BigDecimal("0.01"), new BigDecimal("0.02"), occurrence),
                new KalshiMarket("TEST-HOMAWA-TIE", "Tie", new BigDecimal("0.49"), new BigDecimal("0.50"), occurrence),
                new KalshiMarket("TEST-HOMAWA-AWA", "Away FC", new BigDecimal("0.49"), new BigDecimal("0.50"), occurrence)))),
                NOW);
        Event event = events.findByKalshiEventTicker("TEST-HOMAWA").orElseThrow();

        List<Edge> flagged = detector.evaluate(event, NOW);

        assertEquals(1, flagged.size());
        assertEquals(Outcome.HOME, flagged.get(0).outcome());
        ModelEstimate estimate = detector.refreshEstimate(event, NOW);
        assertEquals(1, edgeLogs.findByModelEstimate(estimate).size());
    }

    @Test
    void reusesEstimateWhenExpectedGoalsUnchanged() {
        Event played = new Event("Test United", "Test City", NOW.minus(Duration.ofDays(10)), null);
        played.recordResult(2, 0);
        events.save(played);
        Event upcoming = events.save(new Event("Test United", "Test City", NOW.plus(Duration.ofDays(3)), null));

        ModelEstimate first = detector.refreshEstimate(upcoming, NOW);
        ModelEstimate second = detector.refreshEstimate(upcoming, NOW.plusSeconds(600));
        assertEquals(first.getId(), second.getId());
    }
}
