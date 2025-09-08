package org.example.telegram_bot_one.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.example.telegram_bot_one.bot.model.MessageTask;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.example.telegram_bot_one.bot.enums.MessageType;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MessageQueueService {
    private final TelegramClient telegramClient;
    private final StringRedisTemplate redisTemplate;
    // для сериализации/десериализации объектов в JSON
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public MessageQueueService(TelegramClient telegramClient, StringRedisTemplate redisTemplate) {
        this.telegramClient = telegramClient;
        this.redisTemplate = redisTemplate;
    }

    private static final String REDIS_QUEUE = "telegram:queue";

    // Лимиты
    // глобальный лимит на все чаты: 30 сообщений в секунду
    private final RateLimiter globalRateLimiter = RateLimiter.create(30.0);

    // карта для хранения времени последнего сообщения в каждом чате
    // лимит на чат: 1 сообщение в секунду
    private final Map<Long, Instant> chatLastMessageTime = new ConcurrentHashMap<>();

    // Методы для добавления сообщений в очередь
    // Для текста без клавиатуры
    public void addTextMessage(Long chatId, String text) {
        enqueue(new MessageTask(chatId, text, null, MessageType.TEXT));
    }

    // Для текста с клавиатурой
    public void addTextMessage(Long chatId, String text, InlineKeyboardMarkup replyMarkup) {
        enqueue(new MessageTask(chatId, text, replyMarkup));
    }
    public void addTextMessage(Long chatId, String text, ReplyKeyboardMarkup replyKeyboardMarkup) {
        enqueue(new MessageTask(chatId, text, replyKeyboardMarkup));
    }

    public void addPhotoMessage(Long chatId, String photoUrl, String caption) {
        // создаём задачу типа PHOTO и помещаем в очередь
        enqueue(new MessageTask(chatId, caption, photoUrl, MessageType.PHOTO));
    }

    private void enqueue(MessageTask task) {
        try {
            // сериализуем задачу в JSON и добавляем в конец Redis-списка
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().rightPush(REDIS_QUEUE, json);
            log.debug("Task enqueued: {}", json);
        } catch (Exception e) {
            log.error("Failed to enqueue task", e);
        }
    }

    // Планировщик очереди
    @Scheduled(fixedRate = 200)
    public void processQueue() {
        try {
            // FIFO: достаем задачу слева из списка
            String json = redisTemplate.opsForList().leftPop(REDIS_QUEUE);
            if (json != null) {
                MessageTask task = objectMapper.readValue(json, MessageTask.class);
                // Проверка nextRetryTime: если ещё рано отправлять задачу
                if (task.nextRetryTime.isAfter(Instant.now())) {
                    // возвращаем задачу в конец очереди
                    requeue(task);
                    return;
                }
                // пытаемся отправить задачу
                processTask(task);
            }
            // Логирование размера очереди для отладки
            Long queueSize = redisTemplate.opsForList().size(REDIS_QUEUE);
            redisTemplate.opsForValue().set("debug:lastQueueSize", queueSize.toString());
        } catch (Exception e) {
            log.error("Error processing queue", e);
        }
    }

    // Обработка одной задачи
    private void processTask(MessageTask task) {
        try {
            // соблюдаем глобальный лимит (30 сообщений/сек)
            globalRateLimiter.acquire();

            // соблюдаем лимит на чат (1 сообщение/сек)
            applyChatRateLimit(task.chatId);

            boolean success = sendTask(task); // отправляем сообщение

            if (!success && task.attempts.get() < MessageTask.MAX_ATTEMPTS) {
                // если не удалось отправить, повторяем с backoff
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
            // если с прошлого сообщения прошло меньше 1 секунды
            long diff = Instant.now().toEpochMilli() - last.toEpochMilli();
            if (diff < 1000) Thread.sleep(1000 - diff); // ждём оставшееся время
        }
        chatLastMessageTime.put(chatId, Instant.now()); // обновляем время последнего сообщения
    }

    private boolean sendTask(MessageTask task) {
        return switch (task.type) {
            case TEXT -> sendText(task);
            case PHOTO -> sendPhoto(task);
        };
    }

    private boolean sendText(MessageTask task) {
        SendMessage msg = SendMessage.builder()
                .chatId(task.chatId.toString())
                .text(task.text)
                .build();
        if (task.replyMarkup != null) {
            msg.setReplyMarkup(task.replyMarkup);
        }
        if( task.replyKeyboardMarkup != null) {
            msg.setReplyMarkup(task.replyKeyboardMarkup);
        }

        try {
            telegramClient.execute(msg);
            log.info("Sent text to chatId={}", task.chatId);
            return true;
        } catch (TelegramApiRequestException e) {
            handleRateLimit(e, task);
            return false;
        } catch (Exception e) {
            log.error("SendText failed for chatId={}", task.chatId, e);
            return false;
        }
    }

    private boolean sendPhoto(MessageTask task) {
        try (InputStream in = new URL(task.photoUrl).openStream()) {
            SendPhoto photo = SendPhoto.builder()
                    .chatId(task.chatId.toString())
                    .caption(task.text)
                    .photo(new InputFile(in, "image.jpg"))
                    .build();


            if (task.replyMarkup instanceof InlineKeyboardMarkup ik) {
                photo.setReplyMarkup(ik);
            }

            telegramClient.execute(photo);
            log.info("Sent photo to chatId={}", task.chatId);
            return true;
        } catch (TelegramApiRequestException e) {
            handleRateLimit(e, task);
            return false;
        } catch (Exception e) {
            log.error("SendPhoto failed for chatId={}", task.chatId, e);
            return false;
        }
    }

    // Обработка ошибок Telegram (429 Too Many Requests)
    private void handleRateLimit(TelegramApiRequestException e, MessageTask task) {
        String response = e.getApiResponse();
        if (response != null && response.contains("429")) {
            int retrySeconds = 5;
            try {
                retrySeconds = Integer.parseInt(response.replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {
            }
            log.info("Rate limited for chatId={}, retry after {}s", task.chatId, retrySeconds);
            task.nextRetryTime = Instant.now().plusSeconds(retrySeconds); // ставим время следующей попытки
            requeue(task); // возвращаем в очередь
        } else {
            log.error("Telegram API error for chatId={}: {}", task.chatId, e.getMessage(), e);
        }
    }

    // Повторная постановка в очередь с backoff
    private void requeueWithBackoff(MessageTask task) {
        task.attempts.incrementAndGet(); // увеличиваем счётчик попыток
        task.nextRetryTime = Instant.now().plusSeconds((long) Math.pow(2, task.attempts.get()));
        // экспоненциальный backoff: 2^attempts секунд
        requeue(task);
    }

    private void requeue(MessageTask task) {
        try {
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().rightPush(REDIS_QUEUE, json); // возвращаем задачу в очередь
        } catch (Exception e) {
            log.error("Failed to requeue task for chatId={}", task.chatId, e);
        }
    }
}
