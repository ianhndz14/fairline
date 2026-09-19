package io.github.ianhndz14.fairline.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    List<PriceSnapshot> findByMarketOrderByCapturedAt(Market market);
}
