package az.aqroinvest.repo;

import az.aqroinvest.domain.Auction;
import az.aqroinvest.domain.AuctionStatus;
import az.aqroinvest.domain.Plot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    /** Row lock for bidding / status changes: one writer per auction at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);

    List<Auction> findByStatusIn(Collection<AuctionStatus> statuses);

    List<Auction> findAllByOrderByStartsAtDesc();

    List<Auction> findByPlotOrderByStartsAtDesc(Plot plot);

    long countByStatusIn(Collection<AuctionStatus> statuses);

    List<Auction> findByStatus(AuctionStatus status);

    @Query("select max(a.code) from Auction a where a.code like concat(:prefix, '%')")
    String maxCodeWithPrefix(@Param("prefix") String prefix);
}
