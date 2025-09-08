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
    //Сохраняет время последнего сообщения для каждого чата (чтобы не спамить в 1 чат чаще, чем раз в секунду)
    private final Map<Long, Instant> chatLastMessageTime = new ConcurrentHashMap<>();

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

        // Если у задачи ещё не наступило время следующей попытки (nextRetryTime) — вернем её в очередь
        if (task.nextRetryTime.isAfter(Instant.now())) {
            queue.push(task);
            return;
        }

        processTask(task);
    }

    private void processTask(MessageTask task) {
        try {
            globalRateLimiter.acquire(); // Guava контролирует максимальное количество сообщений в секунду.
            applyChatRateLimit(task.chatId); // проверяем, можно ли писать в конкретный чат

            boolean success = messageSender.sendTask(task);
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

    private void applyChatRateLimit(Long chatId) throws InterruptedException {
        Instant last = chatLastMessageTime.get(chatId);
        if (last != null) {
            long diff = Instant.now().toEpochMilli() - last.toEpochMilli();
            if (diff < 1000) Thread.sleep(1000 - diff);
        }
        chatLastMessageTime.put(chatId, Instant.now());// обновляем время последней отправки
    }

    // Если задача не удалась — увеличиваем счётчик попыток и ставим время повторной отправки
    private void requeueWithBackoff(MessageTask task) {
        task.attempts.incrementAndGet();
        task.nextRetryTime = Instant.now().plusSeconds((long) Math.pow(2, task.attempts.get()));
        queue.push(task);
    }
}
