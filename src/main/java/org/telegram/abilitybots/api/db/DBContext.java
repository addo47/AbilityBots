package org.telegram.abilitybots.api.db;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DBContext extends Closeable {
  <T> List<T> getList(String name);

  <K, V> Map<K, V> getMap(String name);

  <T> Set<T> getSet(String name);

  String summary();

  Object backup();

  boolean recover(Object backup);

  String info(String name);

  void commit();

  void clear();

  boolean contains(String name);
}