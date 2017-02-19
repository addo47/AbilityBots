package org.telegram.abilitybots.api.db;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static org.mapdb.Serializer.JAVA;

/**
 * Created by Addo on 1/28/2017.
 */
public class MapDBContext implements DBContext {
    private static final String ADMINS = "ADMINS";
    private static final String SUPER_ADMINS = "SUPER_ADMINS";

    private DB db;
    private static DBContext instance;

    private MapDBContext(boolean online) {
        if (online)
            db = DBMaker
                    .fileDB("database")
                    .fileMmapEnableIfSupported()
                    .closeOnJvmShutdown()
                    .transactionEnable()
                    .make();
        else
            db = DBMaker
                    .fileDB("database-offline")
                    .fileMmapEnableIfSupported()
                    .closeOnJvmShutdown()
                    .cleanerHackEnable()
                    .transactionEnable()
                    .fileDeleteAfterClose()
                    .make();
    }

    public static DBContext onlineInstance() {
        return getDbContext(true);
    }

    public static DBContext offlineInstance() {
        return getDbContext(false);
    }

    @NotNull
    private static DBContext getDbContext(boolean online) {
        if (instance == null) {
            return instance = new MapDBContext(online);
        } else {
            return instance;
        }
    }

    @Override
    public <T> List<T> getList(String name) {
        return (List<T>) db.indexTreeList(name, JAVA).createOrOpen();
    }

    @Override
    public <K, V> Map<K, V> getMap(String name) {
        return db.hashMap(name, JAVA, JAVA).createOrOpen();
    }

    @Override
    public <T> Set<T> getSet(String name) {
        return (Set<T>) db.hashSet(name, JAVA).createOrOpen();
    }

    @Override
    public <T> List<T> getGroupList(String name, long id) {
        return getList(format("%s-%d", name, id));
    }

    @Override
    public <K, V> Map<K, V> getGroupMap(String name, long id) {
        return getMap(format("%s-%d", name, id));
    }

    @Override
    public <T> Set<T> getGroupSet(String name, long id) {
        return getSet(format("%s-%d", name, id));
    }

    @Override
    public synchronized void commit() {
        db.commit();
    }

//    @Override
//    public boolean isSuperAdmin(EndUser user) {
//        return this.<EndUser>getSet(SUPER_ADMINS).contains(user);
//    }
//
//    @Override
//    public boolean isAdmin(EndUser user) {
//        return this.<EndUser>getSet(ADMINS).contains(user);
//    }

//    @TestOnly
    @Override
    public void clear() {
        // TODO: Carry this out in a smart manner, keep track of collections saved
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        db.close();
        db = null;
        instance = null;
    }
}
