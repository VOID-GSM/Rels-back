package com.example.rels.global.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 서비스에서 직접 작성한 사유만 응답에 담는다.
	 * 그 외 예외의 메시지는 내부 정보가 섞일 수 있어 기본 오류 응답에 맡긴다.
	 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
		HttpStatusCode statusCode = e.getStatusCode();
		HttpStatus status = HttpStatus.resolve(statusCode.value());

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				statusCode.value(),
				status != null ? status.getReasonPhrase() : "",
				e.getReason() != null ? e.getReason() : "",
				request.getRequestURI());

		return ResponseEntity.status(statusCode).headers(e.getHeaders()).body(body);
	}
}
