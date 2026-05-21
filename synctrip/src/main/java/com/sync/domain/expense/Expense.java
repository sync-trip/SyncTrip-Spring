package com.sync.domain.expense;

import com.sync.domain.band.Band;
import com.sync.domain.user.User;
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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(name = "ocr_raw", columnDefinition = "JSON")
    private String ocrRaw;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Expense() {
    }

    private Expense(Band band, User payer, String itemName, BigDecimal amount, String currency,
                    String receiptUrl, String ocrRaw, LocalDateTime paidAt) {
        this.band = band;
        this.payer = payer;
        this.itemName = itemName;
        this.amount = amount;
        this.currency = currency;
        this.receiptUrl = receiptUrl;
        this.ocrRaw = ocrRaw;
        this.paidAt = paidAt != null ? paidAt : LocalDateTime.now();
    }

    public static Expense create(Band band, User payer, String itemName, BigDecimal amount,
                                  String currency, String receiptUrl, String ocrRaw, LocalDateTime paidAt) {
        return new Expense(band, payer, itemName, amount, currency, receiptUrl, ocrRaw, paidAt);
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public User getPayer() { return payer; }
    public String getItemName() { return itemName; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReceiptUrl() { return receiptUrl; }
    public String getOcrRaw() { return ocrRaw; }
    public boolean isDeleted() { return isDeleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}