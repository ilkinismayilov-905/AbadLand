package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bids")
@Getter
@Setter
@NoArgsConstructor
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Auction auction;
    @ManyToOne(optional = false)
    private AuctionParticipant participant;
    private long amount;
    private LocalDateTime createdAt;
    /** true if this bid triggered the anti-sniping extension. */
    private boolean extended;
}
