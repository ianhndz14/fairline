package io.github.ianhndz14.fairline.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, Long> {

    Optional<Market> findByEventAndOutcome(Event event, Outcome outcome);

    List<Market> findByEvent(Event event);
}
