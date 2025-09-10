package org.example.telegram_bot_one.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.telegram_bot_one.bot.model.MessageTask;
import org.example.telegram_bot_one.service.IQueueService;
import org.example.telegram_bot_one.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisQueueService implements IQueueService<MessageTask> {

    private final StringRedisTemplate redisTemplate;

    @Value("${REDIS_QUEUE}")
    private String REDIS_QUEUE;

    public RedisQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void push(MessageTask task) {
        try {
            redisTemplate.opsForList().rightPush(REDIS_QUEUE, JsonUtils.toJson(task));// кладём в конец
        } catch (Exception e) {
            log.error("Failed to push to Redis queue", e);
        }
    }

    @Override
    public MessageTask pop() {
        try {
            String json = redisTemplate.opsForList().leftPop(REDIS_QUEUE);// берём из начала
            if (json == null) return null;
            return JsonUtils.fromJson(json, MessageTask.class);
        } catch (Exception e) {
            log.error("Failed to pop from Redis queue", e);
            return null;
        }
    }

    @Override
    public void pushFirst(MessageTask task) {
        try {
            redisTemplate.opsForList().leftPush(REDIS_QUEUE, JsonUtils.toJson(task)); // кладём в начало
        } catch (Exception e) {
            log.error("Failed to pushFirst to Redis queue", e);
        }
    }

    @Override
    public long size() {
        return redisTemplate.opsForList().size(REDIS_QUEUE);
    }
}

