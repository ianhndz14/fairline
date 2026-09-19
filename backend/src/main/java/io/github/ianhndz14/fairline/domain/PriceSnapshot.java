package io.github.ianhndz14.fairline.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;

/** A market's price at a point in time, in dollars per $1 contract (= implied probability). */
@Entity
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Market market;

    private Instant capturedAt;
    private BigDecimal yesBid;
    private BigDecimal yesAsk;

    protected PriceSnapshot() {}

    public PriceSnapshot(Market market, Instant capturedAt, BigDecimal yesBid, BigDecimal yesAsk) {
        this.market = market;
        this.capturedAt = capturedAt;
        this.yesBid = yesBid;
        this.yesAsk = yesAsk;
    }

    public Long getId() { return id; }
    public Market getMarket() { return market; }
    public Instant getCapturedAt() { return capturedAt; }
    public BigDecimal getYesBid() { return yesBid; }
    public BigDecimal getYesAsk() { return yesAsk; }
}
