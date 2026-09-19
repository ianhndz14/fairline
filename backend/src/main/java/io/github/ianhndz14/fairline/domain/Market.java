package io.github.ianhndz14.fairline.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** One tradable 1X2 outcome of an event; Kalshi lists three binary markets per match. */
@Entity
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private String kalshiTicker;

    protected Market() {}

    public Market(Event event, Outcome outcome, String kalshiTicker) {
        this.event = event;
        this.outcome = outcome;
        this.kalshiTicker = kalshiTicker;
    }

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public Outcome getOutcome() { return outcome; }
    public String getKalshiTicker() { return kalshiTicker; }
}
