package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "auction_participants", uniqueConstraints = @UniqueConstraint(columnNames = {"auction_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AuctionParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Auction auction;
    @ManyToOne(optional = false)
    private User user;
    /** Public label "İştirakçı #n". */
    private int number;
    private boolean depositConfirmed;
    private LocalDateTime registeredAt;
}
