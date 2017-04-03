package org.telegram.abilitybots.api.sender;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.logging.BotLogger;

import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.WARNING;

/**
 * Created by Addo on 2/9/2017.
 */
public class MessageSenderImpl implements MessageSender {
    private static final String TAG = MessageSender.class.getName();

    private DefaultAbsSender bot;

    public MessageSenderImpl(DefaultAbsSender bot) {
        this.bot = bot;
    }

    @Override
    public Optional<Message> send(String message, long id) {
        return doSendMessage(message, id, false);
    }

    @Override
    public Optional<Message> sendFormatted(String message, long id) {
        return doSendMessage(message, id, true);
    }

    @Override
    public Optional<Message> send(SendMessage message) {
        try {
            return ofNullable(bot.sendMessage(message));
        } catch (TelegramApiException e) {
            BotLogger.log(WARNING, TAG, "Error while sending message!", e);
        }
        return empty();
    }

    private Optional<Message> doSendMessage(String txt, long groupId, boolean format) {
        SendMessage smsg = new SendMessage();
        smsg.setChatId(groupId);
        smsg.setText(txt);
        smsg.enableMarkdown(format);

        return send(smsg);
    }
}
