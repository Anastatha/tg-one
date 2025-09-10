package org.example.telegram_bot_one.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.example.telegram_bot_one.bot.model.MessageTask;
import org.example.telegram_bot_one.service.IMessageSender;
import org.example.telegram_bot_one.service.IQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MessageQueueService {
    private final IQueueService<MessageTask> queue;
    private final IMessageSender messageSender;
    private final FloodBlockService floodBlockService;

    //30 сообщений в секунду
    private final RateLimiter globalRateLimiter = RateLimiter.create(30.0);

    // Время, когда в чат можно отправлять следующее сообщение(чтобы не спамить в 1 чат чаще, чем раз в секунду)
    private final Map<Long, RateLimiter> chatRateLimiters = new ConcurrentHashMap<>();

    public MessageQueueService(IQueueService<MessageTask> queue, IMessageSender messageSender, FloodBlockService floodBlockService) {
        this.queue = queue;
        this.messageSender = messageSender;
        this.floodBlockService = floodBlockService;
    }

    public void addMessage(MessageTask task) {
        queue.push(task);
    }

    // Каждые 5 раз в секунду запускается обработка очереди
    @Scheduled(fixedRate = 200)
    public void processQueue() {
        if (floodBlockService.isBlocked()) {
            return;
        }
        globalRateLimiter.acquire();

        MessageTask task = queue.pop();
        if (task == null) return;

        processTask(task);
    }

    private void processTask(MessageTask task) {
        try {
            RateLimiter chatLimiter = chatRateLimiters.computeIfAbsent(
                    task.chatId,
                    chatId -> RateLimiter.create(1.0)
            );
            chatLimiter.acquire();

            boolean success = messageSender.sendTask(task);

            if (!success) {
                queue.pushFirst(task);
            }
        } catch (Exception e) {
            log.error("Task failed for chatId={}", task.chatId, e);
            queue.pushFirst(task);
        }
    }
}
