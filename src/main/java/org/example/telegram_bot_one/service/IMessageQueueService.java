package org.example.telegram_bot_one.service;

import org.example.telegram_bot_one.bot.model.MessageTask;

public interface IMessageQueueService {
    void addMessage(MessageTask task);
    void processQueue();
    void processTask(MessageTask task);
}
