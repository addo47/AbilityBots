package org.telegram.abilitybots.api.objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.telegram.telegrambots.api.objects.Update;

import java.util.Arrays;

public class MessageContext {
    private final EndUser user;
    private final Long chatId;
    private final String[] arguments;
    private final Update update;

    public MessageContext(Update update, EndUser user, Long chatId, String... arguments) {
        this.user = user;
        this.chatId = chatId;
        this.update = update;
        this.arguments = arguments;
    }

    public EndUser user() {
        return user;
    }

    public Long chatId() {
        return chatId;
    }

    public String[] arguments() {
        return arguments;
    }

    public String firstArg() {
        return arguments[0];
    }

    public String secondArg() {
        return arguments[1 % arguments.length];
    }

    public String thirdArg() {
        return arguments[2 % arguments.length];
    }

    public Update update() {
        return update;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("user", user)
                .add("chatId", chatId)
                .add("arguments", arguments)
                .add("update", update)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        MessageContext that = (MessageContext) o;
        return Objects.equal(user, that.user) &&
                Objects.equal(chatId, that.chatId) &&
                Arrays.equals(arguments, that.arguments) &&
                Objects.equal(update, that.update);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(user, chatId, arguments, update);
    }
}
