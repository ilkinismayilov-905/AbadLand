package az.aqroinvest.repo;

import az.aqroinvest.domain.Auction;
import az.aqroinvest.domain.AuctionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuctionParticipantRepository extends JpaRepository<AuctionParticipant, Long> {
    Optional<AuctionParticipant> findByAuctionAndUserEmailIgnoreCase(Auction auction, String email);

    long countByAuction(Auction auction);

    List<AuctionParticipant> findByUserEmailIgnoreCaseOrderByIdDesc(String email);
}
