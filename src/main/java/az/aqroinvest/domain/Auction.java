package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Open ascending auction for leasing or selling a plot (SPEC "Open auction"). Times are Asia/Baku. */
@Entity
@Table(name = "auctions")
@Getter
@Setter
@NoArgsConstructor
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String code;
    @ManyToOne(optional = false)
    private Plot plot;
    @Enumerated(EnumType.STRING)
    private DealType dealType;
    private long startPrice;
    private long step;
    private long deposit;
    private Integer leaseYears;
    @Column(length = 3000)
    private String rules;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private int extendSeconds = 120;
    @Enumerated(EnumType.STRING)
    private AuctionStatus status;
    private Long currentPrice;
    @ManyToOne
    private User winner;
    private Long finalPrice;
    private int extensions;
    @Column(length = 1000)
    private String comment;
    @ManyToOne
    private User createdBy;
    @ManyToOne
    private User approvedBy;
    @ManyToOne
    private User confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    @Version
    private long version;

    /** Minimum acceptable next bid. */
    public long minNextBid() {
        return currentPrice == null ? startPrice : currentPrice + step;
    }

    /** Price increase vs start, % (null without a price). */
    public Double upliftPct() {
        Long p = finalPrice != null ? finalPrice : currentPrice;
        return p == null || startPrice <= 0 ? null : Math.round((p - startPrice) * 1000.0 / startPrice) / 10.0;
    }
}
