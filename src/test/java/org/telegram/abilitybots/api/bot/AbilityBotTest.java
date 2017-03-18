package org.telegram.abilitybots.api.bot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.ArrayUtils.addAll;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.telegram.abilitybots.api.bot.AbilityBot.*;
import static org.telegram.abilitybots.api.bot.DefaultBot.getDefaultBuilder;
import static org.telegram.abilitybots.api.db.MapDBContext.offlineInstance;
import static org.telegram.abilitybots.api.objects.Flag.DOCUMENT;
import static org.telegram.abilitybots.api.objects.Flag.MESSAGE;
import static org.telegram.abilitybots.api.objects.Locality.ALL;
import static org.telegram.abilitybots.api.objects.Locality.GROUP;
import static org.telegram.abilitybots.api.objects.Privacy.*;

public class AbilityBotTest {
    public static final String[] EMPTY_ARRAY = {};
    public static final long GROUP_ID = 10L;
    public static final String[] TEXT = {"test"};
    public static final EndUser MUSER = new EndUser("name", 1, "username");
    public static final EndUser CREATOR = new EndUser("creator", 1337, "creatorusername");

    private DefaultBot defaultBot;
    private DBContext db;

    @Before
    public void setUp() {
        db = offlineInstance();
        defaultBot = new DefaultBot(EMPTY, EMPTY, db);
    }

    @Test
    public void canPromoteToAdmin() {
        db.<EndUser>getSet(USERS).add(MUSER);

        MessageContext context = mock(MessageContext.class);
        when(context.chatId()).thenReturn(GROUP_ID);
        when(context.user()).thenReturn(CREATOR);
        when(context.firstArg()).thenReturn(MUSER.username());

        defaultBot.promoteAdmin().consumer().accept(context);

        Set<Integer> actual = db.getGroupSet(ADMINS, GROUP_ID);
        Set<Integer> expected = newHashSet(MUSER.id());
        assertEquals("Could not promote user to Admin", expected, actual);
    }

    @Test
    public void canDemoteToUser() {
        db.<EndUser>getSet(USERS).add(MUSER);
        db.<Integer>getGroupSet(ADMINS, GROUP_ID).add(MUSER.id());

        MessageContext context = mock(MessageContext.class);
        when(context.chatId()).thenReturn(GROUP_ID);
        when(context.user()).thenReturn(CREATOR);
        when(context.firstArg()).thenReturn(MUSER.username());

        defaultBot.demoteAdmin().consumer().accept(context);

        Set<Integer> actual = db.getGroupSet(ADMINS, GROUP_ID);
        Set<Integer> expected = emptySet();
        assertEquals("Could not demote admin", expected, actual);
    }

    @Test
    public void canSudoToSuper() {
        db.<EndUser>getSet(USERS).add(MUSER);
        db.<Integer>getSet(SUPER_ADMINS).add(MUSER.id());

        MessageContext context = mock(MessageContext.class);
        when(context.user()).thenReturn(CREATOR);
        when(context.firstArg()).thenReturn(MUSER.username());

        defaultBot.demoteSuper().consumer().accept(context);

        Set<Integer> actual = db.getSet(SUPER_ADMINS);
        Set<Integer> expected = emptySet();
        assertEquals("Could not sudont super-admin", expected, actual);
    }

    @Test
    public void canSudontToUser() {
        db.<EndUser>getSet(USERS).add(MUSER);

        MessageContext context = mock(MessageContext.class);
        when(context.user()).thenReturn(CREATOR);
        when(context.firstArg()).thenReturn(MUSER.username());

        defaultBot.promoteSuper().consumer().accept(context);

        Set<Integer> actual = db.getSet(SUPER_ADMINS);
        Set<Integer> expected = newHashSet(MUSER.id());
        assertEquals("Could not sudo user", expected, actual);
    }

    @Test
    public void canBanUser() {
        db.<EndUser>getSet(USERS).add(MUSER);

        MessageContext context = mock(MessageContext.class);
        when(context.user()).thenReturn(CREATOR);
        when(context.firstArg()).thenReturn(MUSER.username());

        defaultBot.banUser().consumer().accept(context);

        Set<Integer> actual = db.getSet(BLACKLIST);
        Set<Integer> expected = newHashSet(MUSER.id());
        assertEquals("The ban was not emplaced", expected, actual);
    }

    @Test
    public void canUnbanUser() {
        db.<EndUser>getSet(USERS).add(MUSER);
        db.<Integer>getSet(BLACKLIST).add(MUSER.id());

        MessageContext context = mock(MessageContext.class);
        when(context.user()).thenReturn(CREATOR);
        when(context.firstArg()).thenReturn(MUSER.username());

        defaultBot.unbanUser().consumer().accept(context);

        Set<Integer> actual = db.getSet(BLACKLIST);
        Set<Integer> expected = newHashSet();
        assertEquals("The ban was not lifted", expected, actual);
    }

    @Test
    public void cannotBanCreator() {
        db.<EndUser>getSet(USERS).add(MUSER);
        db.<EndUser>getSet(USERS).add(CREATOR);
        MessageContext context = mock(MessageContext.class);
        when(context.user()).thenReturn(MUSER);
        when(context.firstArg()).thenReturn(CREATOR.username());

        defaultBot.banUser().consumer().accept(context);

        Set<Integer> actual = db.getSet(BLACKLIST);
        Set<Integer> expected = newHashSet(MUSER.id());
        assertEquals("Impostor was not added to the blacklist", expected, actual);
    }

    @Test
    public void creatorCanClaimBot() {
        MessageContext context = mock(MessageContext.class);
        when(context.user()).thenReturn(CREATOR);

        defaultBot.claimCreator().consumer().accept(context);

        Set<Integer> actual = db.getSet(SUPER_ADMINS);
        Set<Integer> expected = newHashSet(CREATOR.id());
        assertEquals("Creator was not properly added to the super admins set", expected, actual);
    }

    @Test
    public void userGetsBannedIfClaimsBot() {
        db.<EndUser>getSet(USERS).add(MUSER);
        MessageContext context = mock(MessageContext.class);
        when(context.user()).thenReturn(MUSER);

        defaultBot.claimCreator().consumer().accept(context);

        Set<Integer> actual = db.getSet(BLACKLIST);
        Set<Integer> expected = newHashSet(MUSER.id());
        assertEquals("Could not find user on the blacklist", expected, actual);

        actual = db.getSet(SUPER_ADMINS);
        expected = emptySet();
        assertEquals("Super admins set is not empty", expected, actual);
    }

    @Test
    public void canAddUser() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(user.getId()).thenReturn(MUSER.id());
        when(user.getFirstName()).thenReturn(MUSER.name());
        when(user.getUserName()).thenReturn(MUSER.username());
        when(message.getFrom()).thenReturn(user);
        when(update.getMessage()).thenReturn(message);

        defaultBot.addUser(update);

        Set<EndUser> actual = db.getSet(USERS);
        Set<EndUser> expected = newHashSet(MUSER);
        assertEquals("User was not added", expected, actual);
    }

    @Test
    public void canEditUser() {
        db.<EndUser>getSet(USERS).add(MUSER);
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        String newUsername = MUSER.username() + "-test";
        String newName = MUSER.name() + "-test";
        int sameId = MUSER.id();
        EndUser changedUser = new EndUser(newName, sameId, newUsername);

        when(user.getId()).thenReturn(changedUser.id());
        when(user.getFirstName()).thenReturn(changedUser.name());
        when(user.getUserName()).thenReturn(changedUser.username());
        when(message.getFrom()).thenReturn(user);
        when(update.getMessage()).thenReturn(message);

        defaultBot.addUser(update);

        Set<EndUser> actual = db.getSet(USERS);
        Set<EndUser> expected = newHashSet(changedUser);
        assertEquals("User was not properly edited", expected, actual);
    }

    @Test
    public void canValidateAbility() {
        Tuple2<Update, Ability> invalidTuple = Tuples.of(null, null);
        Ability validAbility = getDefaultBuilder().build();
        Tuple2<Update, Ability> validTuple = Tuples.of(null, validAbility);

        assertEquals("Bot can't validate ability properly", false, defaultBot.validateAbility(invalidTuple));
        assertEquals("Bot can't validate ability properly", true, defaultBot.validateAbility(validTuple));
    }

    @Test
    public void canCheckInput() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Ability abilityWithOneInput = getDefaultBuilder()
                .build();
        Ability abilityWithZeroInput = getDefaultBuilder()
                .input(0)
                .build();

        Tuple3<Update, Ability, String[]> tupleOneArg = Tuples.of(update, abilityWithOneInput, TEXT);
        Tuple3<Update, Ability, String[]> tupleZeroArg = Tuples.of(update, abilityWithZeroInput, TEXT);

        assertEquals("Unexpected result when applying token filter", true, defaultBot.checkInput(tupleOneArg));

        tupleOneArg = Tuples.of(update, abilityWithOneInput, addAll(TEXT, TEXT));
        assertEquals("Unexpected result when applying token filter", false, defaultBot.checkInput(tupleOneArg));

        assertEquals("Unexpected result  when applying token filter", true, defaultBot.checkInput(tupleZeroArg));

        tupleZeroArg = Tuples.of(update, abilityWithZeroInput, EMPTY_ARRAY);
        assertEquals("Unexpected result when applying token filter", true, defaultBot.checkInput(tupleZeroArg));
    }


    @Test
    public void canCheckPrivacy() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.api.objects.User user = mock(User.class);
        Ability publicAbility = getDefaultBuilder().privacy(PUBLIC).build();
        Ability adminAbility = getDefaultBuilder().privacy(ADMIN).build();
        Ability superAdminAbility = getDefaultBuilder().privacy(SUPERADMIN).build();
        Ability creatorAbility = getDefaultBuilder().privacy(Privacy.CREATOR).build();

        Tuple3<Update, Ability, String[]> publicTuple = Tuples.of(update, publicAbility, TEXT);
        Tuple3<Update, Ability, String[]> adminTuple = Tuples.of(update, adminAbility, TEXT);
        Tuple3<Update, Ability, String[]> superAdminTuple = Tuples.of(update, superAdminAbility, TEXT);
        Tuple3<Update, Ability, String[]> creatorTuple = Tuples.of(update, creatorAbility, TEXT);

        mockUser(update, message, user);

        assertEquals("Unexpected result when checking for privacy", true, defaultBot.checkPrivacy(publicTuple));
        assertEquals("Unexpected result when checking for privacy", false, defaultBot.checkPrivacy(adminTuple));
        assertEquals("Unexpected result when checking for privacy", false, defaultBot.checkPrivacy(superAdminTuple));
        assertEquals("Unexpected result when checking for privacy", false, defaultBot.checkPrivacy(creatorTuple));
    }

    @Test
    public void canCheckLocality() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        Ability allAbility = getDefaultBuilder().locality(ALL).build();
        Ability userAbility = getDefaultBuilder().locality(Locality.USER).build();
        Ability groupAbility = getDefaultBuilder().locality(GROUP).build();

        Tuple3<Update, Ability, String[]> publicTuple = Tuples.of(update, allAbility, TEXT);
        Tuple3<Update, Ability, String[]> userTuple = Tuples.of(update, userAbility, TEXT);
        Tuple3<Update, Ability, String[]> groupTuple = Tuples.of(update, groupAbility, TEXT);

        mockUser(update, message, user);
        when(message.isUserMessage()).thenReturn(true);

        assertEquals("Unexpected result when checking for locality", true, defaultBot.checkLocality(publicTuple));
        assertEquals("Unexpected result when checking for locality", true, defaultBot.checkLocality(userTuple));
        assertEquals("Unexpected result when checking for locality", false, defaultBot.checkLocality(groupTuple));
    }

    @Test
    public void canRetrieveContext() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        Ability ability = getDefaultBuilder().build();
        Tuple3<Update, Ability, String[]> tuple = Tuples.of(update, ability, TEXT);

        when(message.getChatId()).thenReturn(GROUP_ID);
        mockUser(update, message, user);

        Tuple2<MessageContext, Ability> actualTuple = defaultBot.getContext(tuple);
        Tuple2<MessageContext, Ability> expectedtuple = Tuples.of(new MessageContext(update, MUSER, GROUP_ID, TEXT), ability);

        assertEquals("Unexpected result when checking for locality", expectedtuple, actualTuple);
    }

    @Test
    public void canCheckGlobalFlags() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        assertEquals("Unexpected result when checking for locality", true, defaultBot.checkGlobalFlags(update));

        when(message.hasText()).thenReturn(false);
        assertEquals("Unexpected result when checking for locality", false, defaultBot.checkGlobalFlags(update));
    }

    @Test(expected = ArithmeticException.class)
    public void canConsumeUpdate() {
        Ability ability = getDefaultBuilder()
                .consumer((context) -> {
                    int x = 1 / 0;
                }).build();
        MessageContext context = mock(MessageContext.class);

        Tuple2<MessageContext, Ability> tuple = Tuples.of(context, ability);

        defaultBot.consumeUpdate(tuple);
    }

    @Test
    public void canFetchAbility() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        String text = "/test";
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(update.getMessage().hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);

        Tuple2<Update, Ability> tuple = defaultBot.getAbility(update);

        Ability expected = defaultBot.testAbility();
        Ability actual = tuple.getT2();

        assertEquals("Wrong ability was fetched", expected, actual);
    }

    @Test
    public void canFetchDefaultAbility() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        String text = "test tags";
        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn(text);

        Tuple2<Update, Ability> tuple = defaultBot.getAbility(update);

        Ability expected = defaultBot.defaultAbility();
        Ability actual = tuple.getT2();

        assertEquals("Wrong ability was fetched", expected, actual);
    }

    @Test
    public void canCheckAbilityFlags() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasDocument()).thenReturn(false);
        when(message.hasText()).thenReturn(true);

        Ability documentAbility = getDefaultBuilder().flag(DOCUMENT, MESSAGE).build();
        Ability textAbility = getDefaultBuilder().flag(Flag.TEXT, MESSAGE).build();

        Tuple3<Update, Ability, String[]> docTuple = Tuples.of(update, documentAbility, TEXT);
        Tuple3<Update, Ability, String[]> textTuple = Tuples.of(update, textAbility, TEXT);

        assertEquals("Unexpected result when checking for message flags", false, defaultBot.checkMessageFlags(docTuple));
        assertEquals("Unexpected result when checking for message flags", true, defaultBot.checkMessageFlags(textTuple));
    }

    @After
    public void tearDown() {
        db.clear();
    }

    private void mockUser(Update update, Message message, User user) {
        when(update.getMessage()).thenReturn(message);
        when(message.getFrom()).thenReturn(user);
        when(user.getFirstName()).thenReturn(MUSER.name());
        when(user.getId()).thenReturn(MUSER.id());
        when(user.getUserName()).thenReturn(MUSER.username());
    }
}
