package com.example.rels.auth.domain.auth.store;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix = "app.oauth-state", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryOAuthStateStore implements OAuthStateStore {
private final Map<String, StoredLoginState> stateStore = new ConcurrentHashMap<>();
@Override
public void save(String state, String redirectUri, String codeVerifier, Duration ttl) {
cleanupExpiredStates();
stateStore.put(state, new StoredLoginState(redirectUri, codeVerifier, Instant.now().plus(ttl)));
}
@Override
public Optional<LoginState> consume(String state) {
cleanupExpiredStates();
StoredLoginState stored = stateStore.remove(state);
if (stored == null || stored.expiresAt().isBefore(Instant.now())) {
return Optional.empty();
}
return Optional.of(new LoginState(stored.redirectUri(), stored.codeVerifier()));
}
private void cleanupExpiredStates() {
Instant now = Instant.now();
stateStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
}
private record StoredLoginState(String redirectUri, String codeVerifier, Instant expiresAt) {
}
}