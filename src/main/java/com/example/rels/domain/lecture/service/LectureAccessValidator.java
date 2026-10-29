package com.example.rels.domain.lecture.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.repository.LectureRepository;
import com.example.rels.domain.user.entity.Role;

@Component
public class LectureAccessValidator {

	private final LectureRepository lectureRepository;

	public LectureAccessValidator(LectureRepository lectureRepository) {
		this.lectureRepository = lectureRepository;
	}

	public LectureEntity requireLecture(Long lectureId) {
		return lectureRepository.findById(lectureId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."));
	}

	public LectureEntity requireLectureForUpdate(Long lectureId) {
		return lectureRepository.findByIdForUpdate(lectureId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."));
	}

	public void validateApprovalVisibility(LectureEntity lecture, Long viewerId, Role viewerRole) {
		if (lecture.getApprovalStatus() == ApprovalStatus.APPROVED) return;
		if (viewerRole == Role.ADMIN) return;
		if (isCreator(lecture, viewerId) || lecture.isSpeaker(viewerId)) return;
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "아직 승인되지 않은 강연입니다.");
	}

	public void validateCreator(LectureEntity lecture, Long userId, Role userRole) {
		if (lecture.getCreator() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "강의 생성자 정보가 없습니다.");
		}
		if (userRole == Role.ADMIN) return;
		if (!lecture.getCreator().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "강의 작성자만 수정 또는 삭제할 수 있습니다.");
		}
	}

	public void validateCreatorOrAdmin(LectureEntity lecture, Long userId, Role userRole) {
		if (!canManageEnrollments(lecture, userId, userRole)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "강의 작성자 또는 관리자만 접근 가능합니다.");
		}
	}

	public void validateAdmin(Role userRole) {
		if (userRole != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생회 또는 관리자 권한이 필요합니다.");
		}
	}

	public boolean isCreator(LectureEntity lecture, Long viewerId) {
		return viewerId != null && lecture.getCreator() != null
				&& lecture.getCreator().getId().equals(viewerId);
	}

	private boolean canManageEnrollments(LectureEntity lecture, Long userId, Role userRole) {
		if (userRole == Role.ADMIN) return true;
		return isCreator(lecture, userId);
	}
}
