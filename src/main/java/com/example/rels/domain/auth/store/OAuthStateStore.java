package com.example.rels.domain.auth.store;

import java.time.Duration;
import java.util.Optional;

public interface OAuthStateStore {

    void save(String state, String redirectUri, String codeVerifier, Duration ttl);

    Optional<LoginState> peek(String state);

    void remove(String state);

    record LoginState(String redirectUri, String codeVerifier) {
    }
}