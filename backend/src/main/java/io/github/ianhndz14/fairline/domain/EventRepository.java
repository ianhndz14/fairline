package io.github.ianhndz14.fairline.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByKalshiEventTicker(String kalshiEventTicker);

    Optional<Event> findFirstByHomeTeamAndAwayTeamAndKickoffBetween(String homeTeam, String awayTeam,
                                                                    Instant from, Instant to);

    List<Event> findByHomeGoalsNotNullAndKickoffAfter(Instant since);

    List<Event> findByKickoffAfterOrderByKickoff(Instant now);

    Optional<Event> findFirstByHomeTeamAndAwayTeamAndKickoffAfterOrderByKickoff(String homeTeam, String awayTeam,
                                                                               Instant now);
}
