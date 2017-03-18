package org.telegram.abilitybots.api.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.telegram.telegrambots.logging.BotLogger;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.IOException;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mapdb.Serializer.JAVA;

public class MapDBContext implements DBContext {
    private static final String TAG = DBContext.class.getSimpleName();

    private DB db;
    private static DBContext instance;
    private final ObjectMapper objectMapper;

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

        objectMapper = new ObjectMapper();
        objectMapper.enableDefaultTyping();
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
    public String summary() {
        return stream(db.getAllNames().spliterator(), true)
                .map(name -> {
                    Object struct = db.get(name);
                    if (struct instanceof Set)
                        return format("%s - Set - %d\n", name, ((Set) struct).size());
                    else if (struct instanceof List)
                        return format("%s - List - %d\n", name, ((List) struct).size());
                    else if (struct instanceof Map)
                        return format("%s - Map - %d\n", name, ((Map) struct).size());
                    else
                        return format("%s - N/A\n", name);
                }).reduce(EMPTY, String::concat).trim();
    }

    @Override
    public Object backup() {
        Map<String, Object> collectedMap = localCopy();
        return writeAsString(collectedMap);
    }

    private Map<String, Object> localCopy() {
        return db.getAll().entrySet().stream().map(entry -> {
            Object struct = entry.getValue();
            if (struct instanceof Set)
                return Tuples.of(entry.getKey(), newHashSet((Set) struct));
            else if (struct instanceof List)
                return Tuples.of(entry.getKey(), newArrayList((List) struct));
            else if (struct instanceof Map)
                return Tuples.of(entry.getKey(), newHashMap((Map) struct));
            else
                return Tuples.of(entry.getKey(), struct);
        }).collect(toMap(tuple -> (String) tuple.getT1(), Tuple2::getT2));
    }

    @Override
    public boolean recover(Object backup) {
        Map<String, Object> snapshot = localCopy();
        try {
            Map<String, Object> backupData = objectMapper.readValue(backup.toString(), new TypeReference<HashMap<String, Object>>() {
            });
            doRecover(backupData);
            return true;
        } catch (IOException e) {
            BotLogger.error(format("Could not recover DB data from file with String representation %s", backup), TAG, e);
            // Attempt to fallback to data snapshot before recovery
            doRecover(snapshot);
            return false;
        }
    }

    private void doRecover(Map<String, Object> backupData) {
        clear();
        backupData.entrySet().forEach(entry -> {
            Object value = entry.getValue();
            String name = entry.getKey();

            if (value instanceof Set) {
                Set entrySet = (Set) value;
                getSet(name).addAll(entrySet);
            } else if (value instanceof Map) {
                Map entryMap = (Map) value;
                getMap(name).putAll(entryMap);
            } else if (value instanceof List) {
                List entryList = (List) value;
                getList(name).addAll(entryList);
            } else {
                BotLogger.error(TAG, format("Unable to identify object type during DB recovery, entry name: %s", name));
            }
        });
        commit();
    }

    @Override
    public String setInfo(String setName) {
        return writeAsString(getSet(setName));
    }

    @Override
    public String groupSetInfo(String setName, long id) {
        return writeAsString(getGroupSet(setName, id));
    }


    @Override
    public String listInfo(String listName) {
        return writeAsString(getList(listName));
    }

    @Override
    public String groupListInfo(String listName, long id) {
        return writeAsString(getGroupList(listName, id));
    }

    @Override
    public String mapInfo(String mapName) {
        return writeAsString(getMap(mapName));
    }

    @Override
    public String groupMapInfo(String mapName, long id) {
        return writeAsString(getGroupMap(mapName, id));
    }

    @Override
    public synchronized void commit() {
        db.commit();
    }

    @Override
    public void clear() {
        db.getAllNames().forEach(name -> {
            Object struct = db.get(name);
            if (struct instanceof Collection)
                ((Collection) struct).clear();
            else if (struct instanceof Map)
                ((Map) struct).clear();
        });
        commit();
    }

    @Override
    public void close() throws IOException {
        db.close();
        db = null;
        instance = null;
    }

    private String writeAsString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            BotLogger.info(format("Failed to read the JSON representation of object: %s", obj), TAG, e);
            return "Error reading required data...";
        }
    }
}
