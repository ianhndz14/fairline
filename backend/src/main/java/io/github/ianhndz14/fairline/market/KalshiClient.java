package io.github.ianhndz14.fairline.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads Premier League game markets from Kalshi's public (no API key) market-data API. */
@Component
public class KalshiClient {

    public record KalshiMarket(
            String ticker,
            @JsonProperty("yes_sub_title") String yesSubTitle,
            @JsonProperty("yes_bid_dollars") BigDecimal yesBid,
            @JsonProperty("yes_ask_dollars") BigDecimal yesAsk,
            @JsonProperty("occurrence_datetime") Instant occurrence) {}

    /** One match. Kalshi titles are "Home vs Away". */
    public record KalshiEvent(@JsonProperty("event_ticker") String eventTicker, String title,
                              List<KalshiMarket> markets) {}

    record EventsPage(List<KalshiEvent> events, String cursor) {}

    private final RestClient http;

    public KalshiClient(@Value("${fairline.kalshi.base-url}") String baseUrl) {
        this.http = RestClient.create(baseUrl);
    }

    /** All open EPL game events with their nested markets, following the pagination cursor. */
    public List<KalshiEvent> openEplGames() {
        List<KalshiEvent> all = new ArrayList<>();
        String cursor = "";
        do {
            EventsPage page = http.get()
                    .uri("/events?series_ticker=KXEPLGAME&status=open&with_nested_markets=true&limit=200&cursor={cursor}",
                            cursor)
                    .retrieve()
                    .body(EventsPage.class);
            if (page == null || page.events() == null) break;
            all.addAll(page.events());
            cursor = page.cursor();
        } while (cursor != null && !cursor.isEmpty());
        return all;
    }
}
