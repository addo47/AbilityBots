package org.telegram.abilitybots.api.sender;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;

import java.util.Optional;

/**
 * Created by Addo on 2/9/2017.
 */
public interface MessageSender {
    Optional<Message> send(String message, long id);
    Optional<Message> sendFormatted(String message, long id);
    Optional<Message> send(SendMessage message);
}
