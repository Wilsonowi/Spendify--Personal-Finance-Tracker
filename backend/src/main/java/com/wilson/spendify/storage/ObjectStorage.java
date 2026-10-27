package com.wilson.spendify.storage;

public interface ObjectStorage {
    void put(String key, byte[] bytes);
    byte[] get(String key);
    void delete(String key);
}

