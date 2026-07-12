package com.dha.branch.listener;

import com.dha.branch.cache.QueueCountCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.dha.branch.config.RabbitMQConfig.QUEUE_STATS_QUEUE;

/**
 * Relays queue.events off RabbitMQ into the local QueueCountCache. Payload
 * shape matches what QueueService.publishQueueCountChanged publishes:
 * {branchId, activeCount}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueueEventListener {

    private final QueueCountCache queueCountCache;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = QUEUE_STATS_QUEUE)
    public void onQueueCountChanged(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long branchId = node.path("branchId").asLong();
            Long activeCount = node.path("activeCount").asLong();
            queueCountCache.update(branchId, activeCount);
        } catch (Exception e) {
            log.error("Failed to process queue event: {}", payload, e);
        }
    }
}
