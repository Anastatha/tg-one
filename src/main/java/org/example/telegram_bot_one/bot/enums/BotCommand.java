package org.example.telegram_bot_one.bot.enums;

public enum BotCommand {
    START("/start"),
    KEYBOARD("/keyboard"),
    HELLO("Привет"),
    IMAGE("Картинка"),
    MY_NAME("my_name"),
    RANDOM("random"),
    LONG_PROCESS("long_process");

    private final String value;

    BotCommand(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
