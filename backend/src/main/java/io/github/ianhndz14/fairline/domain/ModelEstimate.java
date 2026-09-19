package io.github.ianhndz14.fairline.domain;

import io.github.ianhndz14.fairline.engine.PoissonModel;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** Poisson model output for an event at a point in time. */
@Entity
public class ModelEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Event event;

    private Instant createdAt;
    private double homeLambda;
    private double awayLambda;
    private double pHome;
    private double pDraw;
    private double pAway;

    protected ModelEstimate() {}

    public ModelEstimate(
            Event event, Instant createdAt, double homeLambda, double awayLambda, PoissonModel.Probabilities p) {
        this.event = event;
        this.createdAt = createdAt;
        this.homeLambda = homeLambda;
        this.awayLambda = awayLambda;
        this.pHome = p.homeWin();
        this.pDraw = p.draw();
        this.pAway = p.awayWin();
    }

    public double probabilityOf(Outcome outcome) {
        return switch (outcome) {
            case HOME -> pHome;
            case DRAW -> pDraw;
            case AWAY -> pAway;
        };
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public double getHomeLambda() {
        return homeLambda;
    }

    public double getAwayLambda() {
        return awayLambda;
    }
}
