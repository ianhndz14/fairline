package io.github.ianhndz14.fairline.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ianhndz14.fairline.domain.Event;
import io.github.ianhndz14.fairline.domain.EventRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** End-to-end smoke test of the plan B flow through the HTTP layer. Each test rolls back. */
@SpringBootTest(properties = "fairline.scheduling.enabled=false")
@Transactional
class FairlineControllerTest {

    private static final String PRICES = """
            {"HOME": {"bid": 0.01, "ask": 0.02},
             "DRAW": {"bid": 0.49, "ask": 0.50},
             "AWAY": {"bid": 0.49, "ask": 0.50}}""";

    @Autowired WebApplicationContext context;
    @Autowired EventRepository events;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        // Guarantees team stats exist even on an empty database (e.g. in CI).
        Event played = new Event("Test United", "Test City", Instant.now().minus(Duration.ofDays(10)), null);
        played.recordResult(1, 1);
        events.save(played);
    }

    private long createTestMatch() throws Exception {
        String body = mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                        {"homeTeam": "Home FC", "awayTeam": "Away FC", "kickoff": "%s"}"""
                        .formatted(Instant.now().plus(Duration.ofDays(2)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.probabilities.homeWin").isNumber())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void manualEventAndPricesShowUpAsOpportunity() throws Exception {
        long id = createTestMatch();

        mvc.perform(post("/api/events/{id}/prices", id).contentType(MediaType.APPLICATION_JSON).content(PRICES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome").value("HOME"));

        mvc.perform(get("/api/opportunities").param("minEdge", "0.05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opportunities[?(@.eventId == %d)].outcome".formatted(id))
                        .value(Matchers.contains("HOME")));
    }

    @Test
    void estimateAcceptsLambdaOverrides() throws Exception {
        // lambda = 1 each: draw = 0.308508, as in PoissonModelTest.
        mvc.perform(get("/api/estimate").param("home", "Home FC").param("away", "Away FC")
                        .param("homeLambda", "1").param("awayLambda", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probabilities.draw")
                        .value(Matchers.closeTo(new BigDecimal("0.308508"), new BigDecimal("0.000001"))))
                .andExpect(jsonPath("$.scoreGrid.length()").value(11));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                        {"homeTeam": "Home FC", "awayTeam": "home fc", "kickoff": "2026-10-01T14:00:00Z"}"""))
                .andExpect(status().isBadRequest());

        long id = createTestMatch();
        mvc.perform(post("/api/events/{id}/prices", id).contentType(MediaType.APPLICATION_JSON)
                        .content(PRICES.replace("\"bid\": 0.01", "\"bid\": 0.90"))) // bid above ask
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/events/{id}/prices", id).contentType(MediaType.APPLICATION_JSON)
                        .content(PRICES.replace("\"ask\": 0.02", "\"ask\": 1.50"))) // not a probability
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/events/{id}/prices", Long.MAX_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content(PRICES))
                .andExpect(status().isNotFound());
    }
}
