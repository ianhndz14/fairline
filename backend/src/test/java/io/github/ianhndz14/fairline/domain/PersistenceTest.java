package io.github.ianhndz14.fairline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.ianhndz14.fairline.engine.PoissonModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against the real PostgreSQL schema created by Flyway. Starting the context also
 * proves the entities match the migrations (ddl-auto=validate). Each test rolls back.
 */
@SpringBootTest(properties = "fairline.scheduling.enabled=false")
@Transactional
class PersistenceTest {

    private static final Instant KICKOFF = Instant.parse("2026-09-20T15:30:00Z");

    @Autowired
    EntityManager em;

    private Event fulhamVsManUtd() {
        Event event = new Event("Fulham", "Manchester United", KICKOFF, "KXEPLGAME-26SEP20FULMUN");
        em.persist(event);
        return event;
    }

    @Test
    void persistsAFullMatchGraph() {
        Event event = fulhamVsManUtd();
        Market home = new Market(event, Outcome.HOME, "KXEPLGAME-26SEP20FULMUN-FUL");
        em.persist(home);
        em.persist(new PriceSnapshot(home, KICKOFF.minusSeconds(3600), new BigDecimal("0.2700"), new BigDecimal("0.2800")));
        ModelEstimate estimate = new ModelEstimate(event, KICKOFF.minusSeconds(3600), 1.2, 1.5,
                PoissonModel.matchProbabilities(1.2, 1.5));
        em.persist(estimate);
        EdgeLog edge = new EdgeLog(home, estimate, KICKOFF.minusSeconds(3600), 0.275);
        em.persist(edge);
        em.flush();
        em.clear(); // force the next read to come from the database, not memory

        EdgeLog loaded = em.find(EdgeLog.class, edge.getId());
        assertEquals(Outcome.HOME, loaded.getMarket().getOutcome());
        assertEquals("Fulham", loaded.getMarket().getEvent().getHomeTeam());
        assertEquals(KICKOFF, loaded.getMarket().getEvent().getKickoff());
        assertEquals(loaded.getModelProb() - 0.275, loaded.getEdge(), 1e-12);
    }

    // IDENTITY ids make Hibernate insert on persist(), so the database rejects the row right there.
    private void assertRejected(Object entity) {
        assertThrows(PersistenceException.class, () -> {
            em.persist(entity);
            em.flush();
        });
    }

    @Test
    void rejectsSecondMarketForSameOutcome() {
        Event event = fulhamVsManUtd();
        em.persist(new Market(event, Outcome.DRAW, null));
        assertRejected(new Market(event, Outcome.DRAW, null));
    }

    @Test
    void rejectsBidAboveAsk() {
        Market market = new Market(fulhamVsManUtd(), Outcome.AWAY, null);
        em.persist(market);
        assertRejected(new PriceSnapshot(market, KICKOFF, new BigDecimal("0.50"), new BigDecimal("0.40")));
    }

    @Test
    void rejectsTeamPlayingItself() {
        assertRejected(new Event("Fulham", "Fulham", KICKOFF, null));
    }
}
