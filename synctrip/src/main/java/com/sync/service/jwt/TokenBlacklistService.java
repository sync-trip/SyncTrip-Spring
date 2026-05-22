package com.sync.service.jwt;

public interface TokenBlacklistService {
    void add(String token, long ttlSeconds);
    boolean isBlacklisted(String token);
}
