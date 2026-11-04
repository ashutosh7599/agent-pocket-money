package com.agentpocketmoney.agentwallet.repository;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AuthorizationHoldRepository extends JpaRepository<AuthorizationHold, UUID> {
    @Query("select h from AuthorizationHold h where h.wallet.id = :walletId " +
            "and (:status is null or h.status = :status) " +
            "and (h.status <> :activeStatus or h.expiresAt > :now) order by h.createdAt desc")
    Page<AuthorizationHold> findWalletHolds(@Param("walletId") UUID walletId,
            @Param("status") AuthorizationHoldStatus status,
            @Param("activeStatus") AuthorizationHoldStatus activeStatus,
            @Param("now") Instant now, Pageable pageable);

    @Query("select coalesce(sum(h.amountMinorUnits), 0) from AuthorizationHold h where h.wallet.id = :walletId and h.status = :status and h.expiresAt > :now")
    long sumActiveAmount(@Param("walletId") UUID walletId,@Param("status") AuthorizationHoldStatus status,@Param("now") Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuthorizationHold h where h.wallet.id = :walletId and h.status = :status and h.expiresAt <= :now")
    List<AuthorizationHold> findExpiredForUpdate(@Param("walletId") UUID walletId,@Param("status") AuthorizationHoldStatus status,@Param("now") Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuthorizationHold h where h.id = :holdId and h.wallet.id = :walletId")
    Optional<AuthorizationHold> findByWalletIdAndIdForUpdate(@Param("walletId") UUID walletId,@Param("holdId") UUID holdId);
    @Query("select distinct h.wallet.id from AuthorizationHold h where h.status = :status and h.expiresAt <= :now")
    List<UUID> findWalletIdsWithExpiredHolds(@Param("status") AuthorizationHoldStatus status,@Param("now") Instant now);
}
