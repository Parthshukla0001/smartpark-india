package com.smartpark.repository;

import com.smartpark.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(UUID eventId);

    /**
     * Retrieves a batch of pending unpublished outbox events using PostgreSQL's
     * row-level locking with SKIP LOCKED. This guarantees that multiple concurrent
     * application instances or threads can safely claim distinct outbox rows without
     * blocking each other or causing double-publishing race conditions.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingEventsForUpdate(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(Long aggregateId);
}
