package io.github.ianhndz14.fairline.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** A detected opportunity: the model and the normalized market price disagree beyond the threshold. */
@Entity
public class EdgeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ModelEstimate modelEstimate;

    private Instant detectedAt;
    private double modelProb;
    private double marketProb;
    private double edge;

    protected EdgeLog() {}

    /** Edge = model probability minus normalized market probability for the market's outcome. */
    public EdgeLog(Market market, ModelEstimate modelEstimate, Instant detectedAt, double marketProb) {
        this.market = market;
        this.modelEstimate = modelEstimate;
        this.detectedAt = detectedAt;
        this.modelProb = modelEstimate.probabilityOf(market.getOutcome());
        this.marketProb = marketProb;
        this.edge = modelProb - marketProb;
    }

    public Long getId() { return id; }
    public Market getMarket() { return market; }
    public ModelEstimate getModelEstimate() { return modelEstimate; }
    public Instant getDetectedAt() { return detectedAt; }
    public double getModelProb() { return modelProb; }
    public double getMarketProb() { return marketProb; }
    public double getEdge() { return edge; }
}
