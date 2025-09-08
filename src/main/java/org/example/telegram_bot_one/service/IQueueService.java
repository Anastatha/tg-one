package org.example.telegram_bot_one.service;

public interface IQueueService<T> {
    void push(T task);
    T pop();
    long size();
}
