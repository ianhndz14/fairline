package io.github.ianhndz14.fairline.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** One soccer match. The final score stays null until the match is played. */
@Entity
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String kalshiEventTicker;
    private String homeTeam;
    private String awayTeam;
    private Instant kickoff;
    private Integer homeGoals;
    private Integer awayGoals;

    protected Event() {}

    public Event(String homeTeam, String awayTeam, Instant kickoff, String kalshiEventTicker) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoff = kickoff;
        this.kalshiEventTicker = kalshiEventTicker;
    }

    public void linkKalshi(String kalshiEventTicker) {
        this.kalshiEventTicker = kalshiEventTicker;
    }

    public void recordResult(int homeGoals, int awayGoals) {
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
    }

    public Long getId() { return id; }
    public String getKalshiEventTicker() { return kalshiEventTicker; }
    public String getHomeTeam() { return homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public Instant getKickoff() { return kickoff; }
    public Integer getHomeGoals() { return homeGoals; }
    public Integer getAwayGoals() { return awayGoals; }
}
