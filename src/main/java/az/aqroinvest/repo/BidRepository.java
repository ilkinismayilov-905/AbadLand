package az.aqroinvest.repo;

import az.aqroinvest.domain.Auction;
import az.aqroinvest.domain.AuctionParticipant;
import az.aqroinvest.domain.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
    Optional<Bid> findFirstByAuctionOrderByAmountDescIdDesc(Auction auction);

    List<Bid> findByAuctionOrderByIdDesc(Auction auction);

    long countByAuction(Auction auction);

    Optional<Bid> findFirstByParticipantOrderByIdDesc(AuctionParticipant participant);
}
