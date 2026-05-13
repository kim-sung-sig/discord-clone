package com.example.discord.storage;

import java.util.LinkedHashSet;
import java.util.Set;

public final class InMemoryObjectStore implements ObjectStore {
    private final Set<String> objectKeys = new LinkedHashSet<>();

    @Override
    public synchronized String presignUpload(String objectKey) {
        return "memory://upload/" + objectKey;
    }

    @Override
    public synchronized String presignDownload(String objectKey) {
        return "memory://download/" + objectKey;
    }

    @Override
    public synchronized void put(String objectKey) {
        objectKeys.add(objectKey);
    }

    @Override
    public synchronized void delete(String objectKey) {
        objectKeys.remove(objectKey);
    }

    @Override
    public synchronized boolean exists(String objectKey) {
        return objectKeys.contains(objectKey);
    }
}
