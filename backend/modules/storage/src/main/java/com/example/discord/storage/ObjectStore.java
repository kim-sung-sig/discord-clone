package com.example.discord.storage;

public interface ObjectStore {
    String presignUpload(String objectKey);

    String presignDownload(String objectKey);

    void put(String objectKey);

    void delete(String objectKey);

    boolean exists(String objectKey);
}
