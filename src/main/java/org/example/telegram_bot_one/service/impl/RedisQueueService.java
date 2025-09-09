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
            // сериализуем задачу в JSON и добавляем в конец Redis-списка
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().rightPush(REDIS_QUEUE, json);
        } catch (Exception e) {
            log.error("Failed to push to Redis queue", e);
        }
    }

    @Override
    public MessageTask pop() {
        try {
            String json = redisTemplate.opsForList().leftPop(REDIS_QUEUE);
            if (json == null) return null;
            return objectMapper.readValue(json, MessageTask.class);
        } catch (Exception e) {
            log.error("Failed to pop from Redis queue", e);
            return null;
        }
    }

    @Override
    public long size() {
        return redisTemplate.opsForList().size(REDIS_QUEUE);
    }
}

//
//package org.example.telegram_bot_one.service.impl;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import lombok.extern.slf4j.Slf4j;
//import org.example.telegram_bot_one.bot.model.MessageTask;
//import org.example.telegram_bot_one.service.IQueueService;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Service;
//
//import java.util.concurrent.atomic.AtomicLong;
//
//@Slf4j
//@Service
//public class RedisQueueService implements IQueueService<MessageTask> {
//
//    private final StringRedisTemplate redisTemplate;
//    private final ObjectMapper objectMapper;
//
//    @Value("${REDIS_QUEUE}")
//    private String REDIS_QUEUE;
//
//    // Counter для сохранения порядка сообщений с одинаковым nextRetryTime
//    private final AtomicLong counter = new AtomicLong();
//
//    public RedisQueueService(StringRedisTemplate redisTemplate) {
//        this.redisTemplate = redisTemplate;
//        this.objectMapper = new ObjectMapper()
//                .registerModule(new JavaTimeModule())
//                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//    }
//
//    @Override
//    public void push(MessageTask task) {
//        try {
//            String json = objectMapper.writeValueAsString(task);
//            long baseTime = task.nextRetryTime != null ? task.nextRetryTime.toEpochMilli() : System.currentTimeMillis();
//            double score = baseTime + counter.incrementAndGet() * 0.0001; // уникальный score
//
//            redisTemplate.opsForZSet().add(REDIS_QUEUE, json, score);
//        } catch (Exception e) {
//            log.error("Failed to push to Redis queue", e);
//        }
//    }
//
//    @Override
//    public MessageTask pop() {
//        try {
//            var items = redisTemplate.opsForZSet()
//                    .rangeByScore(REDIS_QUEUE, 0, System.currentTimeMillis(), 0, 1);
//
//            if (items == null || items.isEmpty()) return null;
//
//            String json = items.iterator().next();
//            redisTemplate.opsForZSet().remove(REDIS_QUEUE, json);
//
//            return objectMapper.readValue(json, MessageTask.class);
//        } catch (Exception e) {
//            log.error("Failed to pop from Redis queue", e);
//            return null;
//        }
//    }
//
//    @Override
//    public long size() {
//        return redisTemplate.opsForZSet().zCard(REDIS_QUEUE);
//    }
//}
