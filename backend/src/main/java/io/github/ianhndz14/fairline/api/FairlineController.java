package io.github.ianhndz14.fairline.api;

import io.github.ianhndz14.fairline.domain.Outcome;
import io.github.ianhndz14.fairline.market.EdgeDetector;
import io.github.ianhndz14.fairline.market.HistoryService;
import io.github.ianhndz14.fairline.market.HistoryService.EventSummary;
import io.github.ianhndz14.fairline.market.HistoryService.History;
import io.github.ianhndz14.fairline.market.HistoryService.TrackRecord;
import io.github.ianhndz14.fairline.market.MatchService;
import io.github.ianhndz14.fairline.market.MatchService.CreatedEvent;
import io.github.ianhndz14.fairline.market.MatchService.Estimate;
import io.github.ianhndz14.fairline.market.MatchService.Opportunity;
import io.github.ianhndz14.fairline.market.MatchService.Price;
import io.github.ianhndz14.fairline.stats.TeamStats;
import io.github.ianhndz14.fairline.stats.TeamStats.TeamStrength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FairlineController {

    public record NewEvent(
            @NotBlank @Size(max = 64) String homeTeam,
            @NotBlank @Size(max = 64) String awayTeam,
            @NotNull Instant kickoff) {}

    /** {@code lastPriceUpdate} lets the dashboard show how fresh the market data is. */
    public record Opportunities(Instant lastPriceUpdate, double minEdge, List<Opportunity> opportunities) {}

    private final MatchService matches;
    private final TeamStats stats;
    private final EdgeDetector edgeDetector;
    private final HistoryService history;

    public FairlineController(
            MatchService matches, TeamStats stats, EdgeDetector edgeDetector, HistoryService history) {
        this.matches = matches;
        this.stats = stats;
        this.edgeDetector = edgeDetector;
        this.history = history;
    }

    @GetMapping("/teams")
    public List<TeamStrength> teams() {
        return stats.activeTeams();
    }

    @GetMapping("/estimate")
    public Estimate estimate(
            @RequestParam String home,
            @RequestParam String away,
            @RequestParam(required = false) Double homeLambda,
            @RequestParam(required = false) Double awayLambda) {
        return matches.estimate(home, away, homeLambda, awayLambda);
    }

    @GetMapping("/opportunities")
    public Opportunities opportunities(@RequestParam(required = false) Double minEdge) {
        double threshold = minEdge != null ? minEdge : edgeDetector.threshold();
        return new Opportunities(
                matches.lastPriceUpdate().orElse(null), threshold, matches.opportunities(threshold, Instant.now()));
    }

    @GetMapping("/events")
    public List<EventSummary> events() {
        return history.pricedEvents(Instant.now());
    }

    @GetMapping("/events/{id}/history")
    public History history(@PathVariable long id) {
        return history.history(id);
    }

    @GetMapping("/track-record")
    public TrackRecord trackRecord() {
        return history.trackRecord(Instant.now());
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedEvent createEvent(@Valid @RequestBody NewEvent body) {
        return matches.createEvent(body.homeTeam().trim(), body.awayTeam().trim(), body.kickoff());
    }

    @PostMapping("/events/{id}/prices")
    public List<Opportunity> recordPrices(@PathVariable long id, @RequestBody Map<Outcome, @Valid Price> prices) {
        return matches.recordPrices(id, prices, Instant.now());
    }

    @ExceptionHandler
    ProblemDetail badInput(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail conflict(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Conflicts with existing data (e.g. duplicate match)");
    }

    @ExceptionHandler
    ProblemDetail notReady(TeamStats.NoResultsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
