package io.github.ianhndz14.fairline.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeLogRepository extends JpaRepository<EdgeLog, Long> {

    List<EdgeLog> findByModelEstimate(ModelEstimate modelEstimate);
}
