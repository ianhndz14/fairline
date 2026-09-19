package io.github.ianhndz14.fairline.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.market.HistoryService.History;
import io.github.ianhndz14.fairline.market.HistoryService.TrackedEdge;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiEvent;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiMarket;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"fairline.scheduling.enabled=false", "fairline.edge.threshold=0.05"})
@Transactional
class HistoryServiceTest {

    private static final Instant KICKOFF = Instant.now().plus(Duration.ofDays(1));
    private static final Instant FIRST_RUN = KICKOFF.minus(Duration.ofHours(3));
    private static final Instant SECOND_RUN = FIRST_RUN.plus(Duration.ofMinutes(10));

    @Autowired
    HistoryService history;

    @Autowired
    PriceIngestion ingestion;

    @Autowired
    EdgeDetector detector;

    @Autowired
    EventRepository events;

    private Event match;

    /** Home underpriced at the first run; the market has moved further by the second. */
    @BeforeEach
    void twoPriceRuns() {
        Event played = new Event("Test United", "Test City", Instant.now().minus(Duration.ofDays(10)), null);
        played.recordResult(1, 1);
        events.save(played);

        capture(FIRST_RUN, "0.10", "0.11");
        capture(SECOND_RUN, "0.05", "0.06");
        match = events.findByKalshiEventTicker("TEST-HOMAWA").orElseThrow();
    }

    private void capture(Instant at, String homeBid, String homeAsk) {
        Instant occurrence = KICKOFF.plus(PriceIngestion.KALSHI_TIME_OFFSET);
        List<Event> priced = ingestion.ingest(
                List.of(new KalshiEvent(
                        "TEST-HOMAWA",
                        "Home FC vs Away FC",
                        List.of(
                                new KalshiMarket(
                                        "TEST-HOMAWA-HOM",
                                        "Home FC",
                                        new BigDecimal(homeBid),
                                        new BigDecimal(homeAsk),
                                        occurrence),
                                new KalshiMarket(
                                        "TEST-HOMAWA-TIE",
                                        "Tie",
                                        new BigDecimal("0.45"),
                                        new BigDecimal("0.46"),
                                        occurrence),
                                new KalshiMarket(
                                        "TEST-HOMAWA-AWA",
                                        "Away FC",
                                        new BigDecimal("0.45"),
                                        new BigDecimal("0.46"),
                                        occurrence)))),
                at);
        priced.forEach(event -> detector.evaluate(event, at));
    }

    @Test
    void historyHasOnePointPerPriceCapture() {
        History h = history.history(match.getId());

        assertEquals(2, h.points().size());
        assertEquals(FIRST_RUN, h.points().get(0).time());
        // First run: mids 0.105 + 0.455 + 0.455 = 1.015, so HOME = 0.105 / 1.015.
        assertEquals(0.103448, h.points().get(0).market().get(Outcome.HOME), 1e-6);
        // Same model estimate at both points, since no new results came in between.
        assertEquals(h.points().get(0).model(), h.points().get(1).model());
    }

    @Test
    void trackRecordJudgesTheClosingEdgeOnce() {
        Instant afterMatch = KICKOFF.plus(Duration.ofHours(3));
        TrackedEdge pending = onlyTestEdge(afterMatch);
        assertNull(pending.hit()); // no result yet
        // Two runs logged HOME; the closing (second) one is used: HOME = 0.055 / 0.965.
        assertEquals(0.056995, pending.market(), 1e-6);

        match.recordResult(2, 1);
        assertTrue(onlyTestEdge(afterMatch).hit());
    }

    private TrackedEdge onlyTestEdge(Instant now) {
        List<TrackedEdge> ours = history.trackRecord(now).edges().stream()
                .filter(t -> t.event().id() == match.getId())
                .toList();
        assertEquals(1, ours.size());
        assertEquals(Outcome.HOME, ours.get(0).outcome());
        return ours.get(0);
    }
}
