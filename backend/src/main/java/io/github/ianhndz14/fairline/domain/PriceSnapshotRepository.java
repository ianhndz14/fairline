package io.github.ianhndz14.fairline.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findFirstByMarketOrderByCapturedAtDesc(Market market);

    Optional<PriceSnapshot> findFirstByOrderByCapturedAtDesc();

    List<PriceSnapshot> findByMarketOrderByCapturedAt(Market market);
}
