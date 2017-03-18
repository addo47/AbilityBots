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

public class MapDBContextTest {

    private static final String TEST = "TEST";
    private DBContext db;

    @Before
    public void setUp() {
        db = MapDBContext.offlineInstance();
    }
    
    @Test
    public void canRecoverDB() throws IOException {
        Set<EndUser> users = db.getSet(USERS);
        users.add(CREATOR);
        users.add(MUSER);

        Set<EndUser> originalSet = newHashSet(users);
        String beforeBackupInfo = db.setInfo(USERS);

        Object jsonBackup = db.backup();
        db.clear();
        boolean recovered = db.recover(jsonBackup);

        Set<EndUser> recoveredSet = db.getSet(USERS);
        String afterRecoveryInfo = db.setInfo(USERS);

        assertEquals("Could not recover database successfully", true, recovered);
        assertEquals("Set info before and after recovery is different", beforeBackupInfo, afterRecoveryInfo);
        assertEquals("Set before and after recovery are not equal", originalSet, recoveredSet);
    }


    @Test
    public void canGetSummary() throws IOException {
        db.getSet(TEST).add("TEST");

        String actualSummary = db.summary();
        // Name - Type - Number of "rows"
        String expectedSummary = TEST + " - Set - 1";

        assertEquals("Actual DB summary does not match that of the expected", expectedSummary, actualSummary);
    }

    @Test
    public void canGetInfo() throws IOException {
        db.getSet(TEST).add(TEST);

        String actualInfo = db.setInfo(TEST);
        // JSON
        String expectedInfo = "[\"TEST\"]";

        assertEquals("Actual DB summary does not match that of the expected", expectedInfo, actualInfo);
    }

    @After
    public void tearDown() throws IOException {
        db.clear();
        db.close();
    }
}
