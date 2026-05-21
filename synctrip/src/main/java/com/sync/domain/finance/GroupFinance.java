package com.sync.domain.finance;

import com.sync.domain.band.Band;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_finance")
public class GroupFinance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_finance_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false, unique = true)
    private Band band;

    @Column(name = "base_currency", nullable = false, length = 10)
    private String baseCurrency;

    protected GroupFinance() {
    }

    public static GroupFinance create(Band band, String baseCurrency) {
        GroupFinance gf = new GroupFinance();
        gf.band = band;
        gf.baseCurrency = baseCurrency;
        return gf;
    }

    public void updateBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public String getBaseCurrency() { return baseCurrency; }
}
