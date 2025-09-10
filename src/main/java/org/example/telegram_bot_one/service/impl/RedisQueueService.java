package org.example.telegram_bot_one.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.example.telegram_bot_one.bot.model.MessageTask;
import org.example.telegram_bot_one.service.IQueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisQueueService implements IQueueService<MessageTask> {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${REDIS_QUEUE}")
    private String REDIS_QUEUE;

    public RedisQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void push(MessageTask task) {
        try {
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().rightPush(REDIS_QUEUE, json); // кладём в конец
        } catch (Exception e) {
            log.error("Failed to push to Redis queue", e);
        }
    }

    @Override
    public MessageTask pop() {
        try {
            String json = redisTemplate.opsForList().leftPop(REDIS_QUEUE); // берём из начала
            if (json == null) return null;
            return objectMapper.readValue(json, MessageTask.class);
        } catch (Exception e) {
            log.error("Failed to pop from Redis queue", e);
            return null;
        }
    }

    @Override
    public void pushFirst(MessageTask task) {
        try {
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().leftPush(REDIS_QUEUE, json); // кладём в начало
        } catch (Exception e) {
            log.error("Failed to pushFirst to Redis queue", e);
        }
    }

    @Override
    public long size() {
        return redisTemplate.opsForZSet().zCard(REDIS_QUEUE);
    }
}

