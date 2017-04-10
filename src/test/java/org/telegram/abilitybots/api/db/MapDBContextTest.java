package org.telegram.abilitybots.api.db;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.telegram.abilitybots.api.objects.EndUser;

import java.io.IOException;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.telegram.abilitybots.api.bot.AbilityBot.USERS;
import static org.telegram.abilitybots.api.bot.AbilityBotTest.CREATOR;
import static org.telegram.abilitybots.api.bot.AbilityBotTest.MUSER;
import static org.telegram.abilitybots.api.db.MapDBContext.offlineInstance;

public class MapDBContextTest {

    private static final String TEST = "TEST";
    private DBContext db;

    @Before
    public void setUp() {
        db = offlineInstance("db");
    }

    @Test
    public void canRecoverDB() throws IOException {
        Set<EndUser> users = db.getSet(USERS);
        users.add(CREATOR);
        users.add(MUSER);

        Set<EndUser> originalSet = newHashSet(users);
        String beforeBackupInfo = db.info(USERS);

        Object jsonBackup = db.backup();
        db.clear();
        boolean recovered = db.recover(jsonBackup);

        Set<EndUser> recoveredSet = db.getSet(USERS);
        String afterRecoveryInfo = db.info(USERS);

        assertEquals("Could not recover database successfully", true, recovered);
        assertEquals("Set info before and after recovery is different", beforeBackupInfo, afterRecoveryInfo);
        assertEquals("Set before and after recovery are not equal", originalSet, recoveredSet);
    }

    @Test
    public void canFallbackDBIfRecoveryFails() throws IOException {
        Set<EndUser> users = db.getSet(USERS);
        users.add(CREATOR);
        users.add(MUSER);

        Set<EndUser> originalSet = newHashSet(users);
        Object jsonBackup = db.backup();
        String corruptBackup = "!@#$" + String.valueOf(jsonBackup);
        boolean recovered = db.recover(corruptBackup);

        Set<EndUser> recoveredSet = db.getSet(USERS);

        assertEquals("Recovery was successful from a CORRUPT backup", false, recovered);
        assertEquals("Set before and after corrupt recovery are not equal", originalSet, recoveredSet);
    }

    @Test
    public void canGetSummary() throws IOException {
        db.getSet(TEST).add(TEST);

        String actualSummary = db.summary();
        // Name - Type - Number of "rows"
        String expectedSummary = TEST + " - Set - 1";

        assertEquals("Actual DB summary does not match that of the expected", expectedSummary, actualSummary);
    }

    @Test
    public void canGetInfo() throws IOException {
        db.getSet(TEST).add(TEST);

        String actualInfo = db.info(TEST);
        // JSON
        String expectedInfo = "TEST - Set - 1";

        assertEquals("Actual DB structure info does not match that of the expected", expectedInfo, actualInfo);
    }

    @Test(expected = IllegalStateException.class)
    public void cantGetInfoFromNonexistentDBStructureName() throws IOException {
        db.info(TEST);
    }

    @After
    public void tearDown() throws IOException {
        db.clear();
        db.close();
    }
}
