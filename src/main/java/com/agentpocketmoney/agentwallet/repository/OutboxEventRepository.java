package com.agentpocketmoney.agentwallet.repository;
import com.agentpocketmoney.agentwallet.model.event.OutboxEvent;
import com.agentpocketmoney.agentwallet.model.event.OutboxStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select e from OutboxEvent e where e.status=:status and e.nextAttemptAt<=:now order by e.createdAt")
 List<OutboxEvent> findDueForUpdate(@Param("status") OutboxStatus status,@Param("now") Instant now,Pageable pageable);
}
