package io.github.ianhndz14.fairline.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import io.github.ianhndz14.fairline.domain.Market;
import io.github.ianhndz14.fairline.domain.MarketRepository;
import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.domain.PriceSnapshotRepository;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiEvent;
import io.github.ianhndz14.fairline.market.KalshiClient.KalshiMarket;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Feeds hand-built Kalshi data (no network) through ingestion into the real database. Each test rolls back. */
@SpringBootTest(properties = "fairline.scheduling.enabled=false")
@Transactional
class PriceIngestionTest {

    private static final Instant OCCURRENCE = Instant.parse("2026-09-20T18:30:00Z");
    private static final Instant KICKOFF = OCCURRENCE.minus(Duration.ofHours(3));
    private static final Instant BEFORE_KICKOFF = KICKOFF.minus(Duration.ofHours(2));

    @Autowired
    PriceIngestion ingestion;

    @Autowired
    EventRepository events;

    @Autowired
    MarketRepository markets;

    @Autowired
    PriceSnapshotRepository snapshots;

    private static KalshiMarket market(String suffix, String name, String bid, String ask) {
        return new KalshiMarket("TEST-HOMAWA-" + suffix, name, new BigDecimal(bid), new BigDecimal(ask), OCCURRENCE);
    }

    private static KalshiEvent testMatch(KalshiMarket... markets) {
        return new KalshiEvent("TEST-HOMAWA", "Home FC vs Away FC", List.of(markets));
    }

    private final KalshiEvent threeMarkets = testMatch(
            market("HOM", "Home FC", "0.47", "0.48"),
            market("TIE", "Tie", "0.25", "0.26"),
            market("AWA", "Away FC", "0.27", "0.28"));

    @Test
    void createsEventMarketsAndSnapshots() {
        ingestion.ingest(List.of(threeMarkets), BEFORE_KICKOFF);

        Event event = events.findByKalshiEventTicker("TEST-HOMAWA").orElseThrow();
        assertEquals("Home FC", event.getHomeTeam());
        assertEquals("Away FC", event.getAwayTeam());
        assertEquals(KICKOFF, event.getKickoff());

        Market draw = markets.findByEventAndOutcome(event, Outcome.DRAW).orElseThrow();
        assertEquals("TEST-HOMAWA-TIE", draw.getKalshiTicker());
        BigDecimal bid = snapshots.findByMarketOrderByCapturedAt(draw).get(0).getYesBid();
        assertEquals(0, bid.compareTo(new BigDecimal("0.25")), "draw bid was " + bid);
        assertEquals(3, markets.findByEvent(event).size());
    }

    @Test
    void secondRunAddsSnapshotsNotDuplicates() {
        ingestion.ingest(List.of(threeMarkets), BEFORE_KICKOFF);
        ingestion.ingest(List.of(threeMarkets), BEFORE_KICKOFF.plusSeconds(600));

        Event event = events.findByKalshiEventTicker("TEST-HOMAWA").orElseThrow();
        assertEquals(3, markets.findByEvent(event).size());
        Market home = markets.findByEventAndOutcome(event, Outcome.HOME).orElseThrow();
        assertEquals(2, snapshots.findByMarketOrderByCapturedAt(home).size());
    }

    @Test
    void skipsMatchesThatAlreadyKickedOff() {
        ingestion.ingest(List.of(threeMarkets), KICKOFF.plusSeconds(60));
        assertTrue(events.findByKalshiEventTicker("TEST-HOMAWA").isEmpty());
    }

    @Test
    void skipsMarketsForUnknownTeams() {
        ingestion.ingest(List.of(testMatch(market("XYZ", "Someone Else", "0.10", "0.11"))), BEFORE_KICKOFF);
        Event event = events.findByKalshiEventTicker("TEST-HOMAWA").orElseThrow();
        assertTrue(markets.findByEvent(event).isEmpty());
    }
}
