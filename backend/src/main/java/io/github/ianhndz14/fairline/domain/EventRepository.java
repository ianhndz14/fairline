package io.github.ianhndz14.fairline.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select e from Event e where e.kickoff > :since"
            + " and exists (select 1 from Market m where m.event = e) order by e.kickoff")
    List<Event> findPricedSince(Instant since);

    Optional<Event> findByKalshiEventTicker(String kalshiEventTicker);

    Optional<Event> findFirstByHomeTeamAndAwayTeamAndKickoffBetween(
            String homeTeam, String awayTeam, Instant from, Instant to);

    List<Event> findByHomeGoalsNotNullAndKickoffAfter(Instant since);

    List<Event> findByKickoffAfterOrderByKickoff(Instant now);

    Optional<Event> findFirstByHomeTeamAndAwayTeamAndKickoffAfterOrderByKickoff(
            String homeTeam, String awayTeam, Instant now);
}
