package org.example.telegram_bot_one.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.example.telegram_bot_one.bot.model.MessageTask;
import org.example.telegram_bot_one.service.IMessageSender;
import org.example.telegram_bot_one.service.IQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MessageQueueService {
    private final IQueueService<MessageTask> queue;
    private final IMessageSender messageSender;

    //30 сообщений в секунду
    private final RateLimiter globalRateLimiter = RateLimiter.create(30.0);

    // Время, когда в чат можно отправлять следующее сообщение(чтобы не спамить в 1 чат чаще, чем раз в секунду)
    private final Map<Long, Instant> chatNextAvailableTime = new ConcurrentHashMap<>();

    public MessageQueueService(IQueueService<MessageTask> queue, IMessageSender messageSender) {
        this.queue = queue;
        this.messageSender = messageSender;
    }

    public void addMessage(MessageTask task) {
        queue.push(task);
    }

    // Каждые 5 раз в секунду запускается обработка очереди
    @Scheduled(fixedRate = 200)
    public void processQueue() {
        MessageTask task = queue.pop();
        if (task == null) return;

        Instant now = Instant.now();

        // Если у задачи ещё не наступило время следующей попытки (nextRetryTime) — вернем её в очередь
        if (task.nextRetryTime.isAfter(Instant.now())) {
            queue.push(task);
            return;
        }

        // Если чат занят — отложим
        Instant chatAvailable = chatNextAvailableTime.getOrDefault(task.chatId, Instant.EPOCH);
        if (chatAvailable.isAfter(now)) {
            queue.push(task);
            return;
        }

        processTask(task, now);
    }

    private void processTask(MessageTask task, Instant now) {
        try {
            globalRateLimiter.acquire();

            boolean success = messageSender.sendTask(task);
            // обновляем время следующей доступной отправки для чата
            chatNextAvailableTime.put(task.chatId, now.plusSeconds(1));

            // Если не удалось отправить и не превышен лимит попыток — вернём задачу в очередь
            if (!success && task.attempts.get() < MessageTask.MAX_ATTEMPTS) {
                requeueWithBackoff(task);
            }
        } catch (Exception e) {
            log.error("Task failed for chatId={}", task.chatId, e);
            if (task.attempts.get() < MessageTask.MAX_ATTEMPTS) {
                requeueWithBackoff(task);
            }
        }
    }

    // Если задача не удалась — увеличиваем счётчик попыток и ставим время повторной отправки
    private void requeueWithBackoff(MessageTask task) {
        task.attempts.incrementAndGet();
        task.nextRetryTime = Instant.now().plusSeconds((long) Math.pow(2, task.attempts.get()));
        queue.push(task);
    }
}
