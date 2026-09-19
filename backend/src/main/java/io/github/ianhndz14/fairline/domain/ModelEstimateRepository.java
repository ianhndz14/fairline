package io.github.ianhndz14.fairline.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelEstimateRepository extends JpaRepository<ModelEstimate, Long> {

    Optional<ModelEstimate> findFirstByEventOrderByCreatedAtDesc(Event event);
}
