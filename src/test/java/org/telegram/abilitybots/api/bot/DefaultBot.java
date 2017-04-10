package org.telegram.abilitybots.api.bot;

import com.google.common.annotations.VisibleForTesting;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.Ability;
import org.telegram.abilitybots.api.objects.Ability.AbilityBuilder;
import org.telegram.abilitybots.api.sender.MessageSender;

import static org.telegram.abilitybots.api.objects.Ability.builder;
import static org.telegram.abilitybots.api.objects.Locality.ALL;
import static org.telegram.abilitybots.api.objects.Privacy.PUBLIC;

/**
 * Created by Addo on 2/11/2017.
 */
public class DefaultBot extends AbilityBot {

    public DefaultBot(String token, String username, DBContext db) {
        super(token, username, db);
    }

    @Override
    public int creatorId() {
        return 1337;
    }

    public Ability defaultAbility() {
        return getDefaultBuilder().name(DEFAULT).info("dis iz default command").build();
    }

    public Ability testAbility() {
        return getDefaultBuilder().build();
    }

    public static AbilityBuilder getDefaultBuilder() {
        return builder()
                .name("test")
                .privacy(PUBLIC)
                .locality(ALL)
                .input(1)
                .consumer(ctx -> {
                });
    }

    @VisibleForTesting
    void setSender(MessageSender sender) {
        this.sender = sender;
    }
}