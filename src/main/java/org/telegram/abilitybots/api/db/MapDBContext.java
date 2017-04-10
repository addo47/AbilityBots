package org.telegram.abilitybots.api.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.telegram.abilitybots.api.util.Pair;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.IOException;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;
import static org.mapdb.Serializer.JAVA;

public class MapDBContext implements DBContext {
    private static final String TAG = DBContext.class.getSimpleName();

    private final DB db;
    private final ObjectMapper objectMapper;

    public MapDBContext(DB db) {
        this.db = db;

        objectMapper = new ObjectMapper();
        objectMapper.enableDefaultTyping();
    }

    public static DBContext onlineInstance(String name) {
        DB db = DBMaker
                .fileDB(name)
                .fileMmapEnableIfSupported()
                .closeOnJvmShutdown()
                .transactionEnable()
                .make();

        return new MapDBContext(db);
    }

    public static DBContext offlineInstance(String name) {
        DB db = DBMaker
                .fileDB(name)
                .fileMmapEnableIfSupported()
                .closeOnJvmShutdown()
                .cleanerHackEnable()
                .transactionEnable()
                .fileDeleteAfterClose()
                .make();

        return new MapDBContext(db);
    }

    @Override
    public <T> List<T> getList(String name) {
        return (List<T>) db.<T>indexTreeList(name, JAVA).createOrOpen();
    }

    @Override
    public <K, V> Map<K, V> getMap(String name) {
        return db.<K, V>hashMap(name, JAVA, JAVA).createOrOpen();
    }

    @Override
    public <T> Set<T> getSet(String name) {
        return (Set<T>) db.<T>hashSet(name, JAVA).createOrOpen();
    }

    @Override
    public <T> List<T> getGroupList(String name, long id) {
        return getList(formatGroupData(name, id));
    }

    @Override
    public <K, V> Map<K, V> getGroupMap(String name, long id) {
        return getMap(formatGroupData(name, id));
    }

    @Override
    public <T> Set<T> getGroupSet(String name, long id) {
        return getSet(formatGroupData(name, id));
    }

    public static String formatGroupData(String name, long id) {
        return format("%s-%d", name, id);
    }

    @Override
    public String summary() {
        return stream(db.getAllNames().spliterator(), true)
                .map(this::info)
                .reduce(new StringJoiner("\n"), StringJoiner::add, StringJoiner::merge)
                .toString();
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
                return Pair.of(entry.getKey(), newHashSet((Set) struct));
            else if (struct instanceof List)
                return Pair.of(entry.getKey(), newArrayList((List) struct));
            else if (struct instanceof Map)
                return Pair.of(entry.getKey(), newHashMap((Map) struct));
            else
                return Pair.of(entry.getKey(), struct);
        }).collect(toMap(pair -> (String) pair.a(), Pair::b));
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
    public String info(String name) {
        Object struct = db.get(name);
        if (isNull(struct))
            throw new IllegalStateException(format("DB structure with name [%s] does not exist", name));

        if (struct instanceof Set)
            return format("%s - Set - %d", name, ((Set) struct).size());
        else if (struct instanceof List)
            return format("%s - List - %d", name, ((List) struct).size());
        else if (struct instanceof Map)
            return format("%s - Map - %d", name, ((Map) struct).size());
        else
            return format("%s - %s", name, struct.getClass().getSimpleName());
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
    public boolean contains(String name) {
        return db.exists(name);
    }

    @Override
    public void close() throws IOException {
        db.close();
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
