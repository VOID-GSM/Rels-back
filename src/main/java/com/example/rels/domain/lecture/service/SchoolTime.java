package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class SchoolTime {

	private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Seoul");

	private SchoolTime() {
	}

	static LocalDateTime now() {
		return LocalDateTime.now(SCHOOL_ZONE);
	}

	static LocalDateTime fromServerTime(LocalDateTime serverTime) {
		return serverTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(SCHOOL_ZONE).toLocalDateTime();
	}
}
