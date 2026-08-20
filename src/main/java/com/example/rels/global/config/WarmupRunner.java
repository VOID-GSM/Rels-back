package com.example.rels.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class WarmupRunner {

    private final WebClient webClient;

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        log.info("[Warm-up] 서버 시작 완료: WebClient 및 외부 커넥션 워밍업을 진행합니다.");
        try {
            webClient.get()
                    .uri("https://httpbin.org/get")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("[Warm-up] WebClient 커넥션 워밍업 성공");
        } catch (Exception e) {
            log.warn("[Warm-up] 워밍업 중 비치명적 에러 발생: {}", e.getMessage());
        }
    }
}