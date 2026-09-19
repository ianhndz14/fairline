package io.github.ianhndz14.fairline.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findFirstByMarketOrderByCapturedAtDesc(Market market);

    Optional<PriceSnapshot> findFirstByOrderByCapturedAtDesc();

    @Query("select s from PriceSnapshot s join fetch s.market m where m.event = :event order by s.capturedAt")
    List<PriceSnapshot> findByEvent(Event event);

    List<PriceSnapshot> findByMarketOrderByCapturedAt(Market market);
}
