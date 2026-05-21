package com.sync.domain.finance;

import com.sync.domain.band.Band;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "group_exchange_rates")
public class GroupExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_exchange_rate_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    // 1 base_currency 당 해당 통화 수량 (ExchangeRate-API 응답값 그대로)
    @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @UpdateTimestamp
    @Column(name = "rate_updated_at", nullable = false)
    private LocalDateTime rateUpdatedAt;

    protected GroupExchangeRate() {
    }

    public static GroupExchangeRate create(Band band, String currency, BigDecimal exchangeRate) {
        GroupExchangeRate r = new GroupExchangeRate();
        r.band = band;
        r.currency = currency;
        r.exchangeRate = exchangeRate;
        return r;
    }

    public void updateRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public String getCurrency() { return currency; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public LocalDateTime getRateUpdatedAt() { return rateUpdatedAt; }
}
