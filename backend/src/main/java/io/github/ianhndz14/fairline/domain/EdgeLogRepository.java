package io.github.ianhndz14.fairline.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EdgeLogRepository extends JpaRepository<EdgeLog, Long> {

    List<EdgeLog> findByModelEstimate(ModelEstimate modelEstimate);

    @Query("select l from EdgeLog l join fetch l.market m join fetch m.event e where e.kickoff < :now")
    List<EdgeLog> findForMatchesBefore(Instant now);
}
