package com.example.rels.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ExecutionTimeAspect {

    @Around("execution(* com.example.rels..*Controller.*dg*Callback*(..)) || execution(* com.example.rels..*Controller.dgCallback(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[API 실측 로깅] 경로: {} | 실행 시간: {} ms", joinPoint.getSignature().toShortString(), totalTime);

        return proceed;
    }
}