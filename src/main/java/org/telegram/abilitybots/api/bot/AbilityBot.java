package org.telegram.abilitybots.api.bot;

import org.apache.commons.io.IOUtils;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.sender.MessageSenderImpl;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.SendDocument;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.logging.BotLogger;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Arrays.stream;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static jersey.repackaged.com.google.common.base.Throwables.propagate;
import static org.telegram.abilitybots.api.db.MapDBContext.onlineInstance;
import static org.telegram.abilitybots.api.objects.Ability.builder;
import static org.telegram.abilitybots.api.objects.Flag.*;
import static org.telegram.abilitybots.api.objects.Locality.*;
import static org.telegram.abilitybots.api.objects.Privacy.*;

/**
 * @author Abbas Abou Daya
 * @version 1.0
 * @brief Parent bot abstract class that helps find and register Abilities found in the calling subclass
 * @date 18th of February, 2016
 */
public abstract class AbilityBot extends TelegramLongPollingBot {
    private static final String TAG = AbilityBot.class.getName();

    // DB objects
    public static final String SUPER_ADMINS = "SUPER_ADMINS";
    public static final String ADMINS = "ADMINS";
    public static final String USERS = "USERS";
    public static final String BLACKLIST = "BLACKLIST";

    // Factory commands
    public static final String DEFAULT = "default";
    public static final String CLAIM = "claim";
    public static final String BAN = "ban";
    public static final String SUDO = "sudo";
    public static final String SUDONT = "sudont";
    public static final String PROMOTE = "promote";
    public static final String DEMOTE = "demote";
    public static final String UNBAN = "unban";

    protected final DBContext db;
    protected MessageSender sender;

    private final String botToken;
    private final String botUsername;

    // Command registry
    private Map<String, Ability> abilities;

    public AbilityBot(String botToken, String botUsername, DBContext db, Class<? extends MessageSender> clazz) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.db = db;

        try {
            this.sender = clazz.getConstructor(DefaultAbsSender.class).newInstance(this);
        } catch (Exception e) {
            BotLogger.error(TAG, "Couldn't construct MessageSender. If this is a custom implementation, make sure to have a constructor that requires a DefaultAbsSender instance.", e);
            throw propagate(e);
        }

        registerAbilities(this);
    }

    public AbilityBot(String token, String username) {
        this(token, username, onlineInstance(), MessageSenderImpl.class);
    }

    public abstract int creatorId();

    @Override
    public void onUpdateReceived(Update update) {
        BotLogger.info(format("New update [%s] received at %s", update.getUpdateId(), now()), TAG);
        BotLogger.info(update.toString(), TAG);

        Stream.of(update)
                .filter(this::checkGlobalFlags)
                .filter(this::checkBlacklist)
                .map(this::addUser)
                .map(this::getAbility)
                .filter(this::validateAbility)
                .filter(this::checkMessageFlags)
                .filter(this::checkPrivacy)
                .filter(this::checkLocality)
                .filter(this::checkInput)
                .map(this::getContext)
                .map(this::consumeUpdate)
                .forEach(this::postConsumption);

        BotLogger.info(format("Processing of update [%s] ended at %s", update.getUpdateId(), now()), TAG);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public Ability commands() {
        return builder()
                .name("commands")
                .locality(ALL)
                .privacy(PUBLIC)
                .flag(TEXT)
                .input(0)
                .consumer(ctx -> {
                    String commands = abilities.entrySet().stream()
                            .filter(entry -> nonNull(entry.getValue().info()))
                            .map(entry -> {
                                String name = entry.getValue().name();
                                String info = entry.getValue().info();
                                return format("%s - %s", name, info);
                            })
                            .reduce((a, b) -> format("%s%n%s", a, b)).orElse("No public commands found.");
                    sender.send(commands, ctx.chatId());
                })
                .build();
    }

    public Ability recover() {
        return builder()
                .name("recover")
                .locality(USER)
                .privacy(CREATOR)
                .flag(DOCUMENT, CAPTION)
                .input(0)
                .consumer(ctx -> {
                    String fileId = ctx.update().getMessage().getDocument().getFileId();
                    try {
                        File backup = downloadFile(getFile(new GetFile().setFileId(fileId)));
                        String backupData = IOUtils.toString(new FileReader(backup));
                        if (db.recover(backupData)) {
                            sender.send("I have successfully recovered.", ctx.chatId());
                        } else {
                            sender.send("Oops, something went wrong during recovery.", ctx.chatId());
                        }

                    } catch (Exception e) {
                        BotLogger.error("Could not recover DB from backup", TAG, e);
                        sender.send("I have failed to recover.", ctx.chatId());
                    }
                })
                .build();
    }

    public Ability backup() {
        return builder()
                .name("backup")
                .locality(USER)
                .privacy(CREATOR)
                .input(0)
                .consumer(ctx -> {
                    File backup = new File("backup.json");

                    try {
                        PrintStream printStream = new PrintStream(backup);
                        printStream.print(db.backup());
                        sendDocument(new SendDocument()
                                .setNewDocument(backup)
                                .setChatId(ctx.chatId())
                        );
                    } catch (FileNotFoundException e) {
                        BotLogger.error("Error while fetching backup", TAG, e);
                    } catch (TelegramApiException e) {
                        BotLogger.error("Error while sending document/backup file", TAG, e);
                    }
                })
                .build();
    }

    public Ability banUser() {
        return builder()
                .name(BAN)
                .locality(ALL)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());
                    Optional<Integer> endUser = getUser(username).map(EndUser::id);

                    endUser.ifPresent(user -> {
                        String actualuser = username;
                        // Protection from abuse
                        if (user == creatorId()) {
                            user = ctx.user().id();
                            actualuser = getUser(user).get().name();
                        }

                        Set<Integer> blacklist = db.getSet(BLACKLIST);

                        if (blacklist.contains(user))
                            sender.sendFormatted(format("%s is already <b>banned</b>.", actualuser), ctx.chatId());
                        else {
                            blacklist.add(user);
                            sender.sendFormatted(format("%s is now <b>banned</b>.", actualuser), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability unbanUser() {
        return builder()
                .name(UNBAN)
                .locality(ALL)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());

                    Optional<Integer> endUser = getUser(username).map(EndUser::id);
                    endUser.ifPresent(user -> {
                        Set<Integer> blacklist = db.getSet(BLACKLIST);

                        if (!blacklist.contains(user))
                            sender.sendFormatted(format("%s is <b>not</b> on the <b>blacklist</b>.", username), ctx.chatId());
                        else {
                            blacklist.remove(user);
                            sender.sendFormatted(format("%s, your ban has been <b>lifted</b>.", username), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability promoteAdmin() {
        return builder()
                .name(PROMOTE)
                .locality(GROUP)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());

                    Optional<Integer> endUser = getUser(username).map(EndUser::id);
                    endUser.ifPresent(user -> {
                        Set<Integer> admins = db.getGroupSet(ADMINS, ctx.chatId());
                        boolean isAdmin = admins.contains(user) || isSuperAdmin(user);

                        if (isAdmin)
                            sender.sendFormatted(format("%s is already an <b>admin</b>.", username), ctx.chatId());
                        else {
                            admins.add(user);
                            sender.sendFormatted(format("%s is now an <b>admin</b>.", username), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability demoteAdmin() {
        return builder()
                .name(DEMOTE)
                .locality(GROUP)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());

                    Optional<Integer> endUserId = getUser(username).map(EndUser::id);
                    endUserId.ifPresent(id -> {
                        Set<Integer> admins = db.getGroupSet(ADMINS, ctx.chatId());
                        if (!admins.contains(id)) {
                            sender.sendFormatted(format("%s <b>not</b> an <b>admin</b>.", username), ctx.chatId());
                        } else {
                            admins.remove(id);
                            sender.sendFormatted(format("%s has been <b>demoted</b>.", username), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability promoteSuper() {
        return builder()
                .name(SUDO)
                .locality(ALL)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());
                    Optional<Integer> endUserId = getUser(username).map(EndUser::id);

                    endUserId.ifPresent(id -> {
                        Set<Integer> superAdmins = db.getSet(SUPER_ADMINS);
                        if (superAdmins.contains(id))
                            sender.sendFormatted(format("%s is already a <b>super admin</b>.", username), ctx.chatId());
                        else {
                            superAdmins.add(id);
                            sender.sendFormatted(format("%s is now a <b>super admin</b>.", username), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability demoteSuper() {
        return builder()
                .name(SUDONT)
                .locality(ALL)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());

                    Optional<Integer> endUserId = getUser(username).map(EndUser::id);

                    endUserId.ifPresent(id -> {
                        Set<Integer> superAdmins = db.getSet(SUPER_ADMINS);
                        if (!superAdmins.contains(id)) {
                            sender.sendFormatted(format("%s is <b>not</b> a <b>super admin</b>.", username), ctx.chatId());
                        } else {
                            superAdmins.remove(id);
                            sender.sendFormatted(format("%s has been <b>demoted</b>.", username), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability claimCreator() {
        return builder()
                .name(CLAIM)
                .locality(USER)
                .privacy(PUBLIC)
                .input(0)
                .consumer(ctx -> {
                    if (ctx.user().id() == creatorId()) {
                        Set<Integer> superadmins = db.getSet(SUPER_ADMINS);
                        setMaster(superadmins, creatorId(), ctx.chatId());
                    } else {
                        // This is not a joke
                        abilities.get(BAN).consumer().accept(new MessageContext(ctx.update(), ctx.user(), ctx.chatId(), ctx.user().username()));
                    }
                })
                .post(commit())
                .build();
    }

    protected <T extends AbilityBot> void registerAbilities(T bot) {
        try {
            abilities = stream(bot.getClass().getMethods())
                    .filter(method -> method.getReturnType().equals(Ability.class))
                    .map(method -> {
                        try {
                            return (Ability) method.invoke(bot);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            BotLogger.error("Could not add ability", TAG, e);
                            throw propagate(e);
                        }
                    })
                    .collect(toMap(Ability::name, identity()));
        } catch (IllegalStateException e) {
            BotLogger.error(TAG, "Duplicates found while registering abilities. Make sure that the abilities declared don't clash with the reserved ones.", e);
            throw propagate(e);
        }

    }

    private String stripTag(String name) {
        String username = name.toLowerCase();
        username = username.startsWith("@") ? username.substring(1, username.length()) : username;
        return username;
    }

    private void setMaster(Set<Integer> admins, int id, long chatId) {
        if (admins.contains(id))
            sender.send("You're already my master.", chatId);
        else {
            admins.add(id);
            sender.send("You're now my master.", chatId);
        }
    }

    private Consumer<MessageContext> commit() {
        return ctx -> db.commit();
    }

    private Optional<EndUser> getUser(String username) {
        return db.<EndUser>getSet(USERS).stream().filter(user -> user.username().equals(username)).findFirst();
    }

    private Optional<EndUser> getUser(int id) {
        return db.<EndUser>getSet(USERS).stream().filter(user -> user.id() == id).findFirst();
    }

    private void postConsumption(Tuple2<MessageContext, Ability> tuple) {
        ofNullable(tuple.getT2().postConsumer())
                .ifPresent(consumer -> consumer.accept(tuple.getT1()));
    }

    Tuple2<MessageContext, Ability> consumeUpdate(Tuple2<MessageContext, Ability> tuple) {
        tuple.getT2().consumer().accept(tuple.getT1());
        return tuple;
    }

    Tuple2<MessageContext, Ability> getContext(Tuple3<Update, Ability, String[]> tuple) {
        Message message = tuple.getT1().getMessage();
        EndUser user = new EndUser(message.getFrom());

        return Tuples.of(new MessageContext(tuple.getT1(), user, message.getChatId(), tuple.getT3()), tuple.getT2());
    }

    boolean checkBlacklist(Update update) {
        return !db.<Integer>getSet(BLACKLIST).contains(update.getMessage().getFrom().getId());
    }

    boolean checkInput(Tuple3<Update, Ability, String[]> tuple) {
        String[] tokens = tuple.getT3();
        int abilityTokens = tuple.getT2().tokens();

        return abilityTokens == 0 || (tokens.length > 0 && tokens.length == abilityTokens);
    }

    boolean checkLocality(Tuple3<Update, Ability, String[]> tuple) {
        Message msg = tuple.getT1().getMessage();
        Locality locality = msg.isUserMessage() ? USER : GROUP;
        Locality abilityLocality = tuple.getT2().locality();
        return abilityLocality == ALL || locality == abilityLocality;
    }

    boolean checkPrivacy(Tuple3<Update, Ability, String[]> tuple) {
        EndUser user = new EndUser(tuple.getT1().getMessage().getFrom());
        boolean isUserMsg = tuple.getT1().getMessage().isUserMessage();
        Long groupId = tuple.getT1().getMessage().getChatId();
        Privacy privacy;
        int id = user.id();
        // Sonar
        if (isCreator(id)) {
            privacy = CREATOR;
        } else {
            privacy = isSuperAdmin(id) ? SUPERADMIN : !isUserMsg && isAdmin(id, groupId) ? ADMIN : PUBLIC;
        }

        return privacy.compareTo(tuple.getT2().privacy()) >= 0;
    }

    private boolean isCreator(int id) {
        return id == creatorId();
    }

    private boolean isSuperAdmin(Integer id) {
        return db.<Integer>getSet(SUPER_ADMINS).contains(id);
    }

    private boolean isAdmin(Integer id, long groupId) {
        return db.<Integer>getGroupSet(ADMINS, groupId).contains(id);
    }

    boolean validateAbility(Tuple2<Update, Ability> tuple) {
        return tuple.getT2() != null;
    }

    Tuple3<Update, Ability, String[]> getAbility(Update update) {
        // Handle updates without messages
        // Passing through this function means that the global flags have passed
        Message msg = update.getMessage();
        if (!update.hasMessage() || !(msg.hasText() || nonNull(msg.getCaption())))
            return Tuples.of(update, abilities.get(DEFAULT), new String[]{});

        // Priority goes to text before captions
        String[] tokens = msg.hasText() ?
                msg.getText().split(" ") :
                msg.getCaption().split(" ");

        if (tokens[0].startsWith("/")) {
            Ability ability = abilities.get(tokens[0].substring(1));
            tokens = Arrays.copyOfRange(tokens, 1, tokens.length);
            return Tuples.of(update, ability, tokens);
        } else {
            Ability ability = abilities.get(DEFAULT);
            return Tuples.of(update, ability, tokens);
        }
    }

    Update addUser(Update update) {
        EndUser endUser = new EndUser(update.getMessage().getFrom());
        Set<EndUser> set = db.getSet(USERS);

        Optional<EndUser> optUser = set.stream().filter(user -> user.id() == endUser.id()).findAny();
        if (optUser.isPresent()) {
            EndUser prevUser = optUser.get();
            if (!prevUser.equals(endUser)) {
                set.remove(prevUser);
                set.add(endUser);
                db.commit();
            }
        } else {
            set.add(endUser);
            db.commit();
        }

        return update;
    }

    boolean checkMessageFlags(Tuple3<Update, Ability, String[]> tuple) {
        Ability ability = tuple.getT2();
        Update update = tuple.getT1();

        return ofNullable(ability.flags())
                .map(flags -> stream(flags)
                        .reduce(true, (a, b) -> a && b.test(update), (a, b) -> a && b))
                .orElse(true);
    }

    boolean checkGlobalFlags(Update update) {
        return MESSAGE.test(update);
    }
}
