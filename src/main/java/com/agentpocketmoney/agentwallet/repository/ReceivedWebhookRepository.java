package com.agentpocketmoney.agentwallet.repository;
import com.agentpocketmoney.agentwallet.model.event.ReceivedWebhook;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReceivedWebhookRepository extends JpaRepository<ReceivedWebhook,UUID> {
 Optional<ReceivedWebhook> findByEventId(UUID eventId);
}
