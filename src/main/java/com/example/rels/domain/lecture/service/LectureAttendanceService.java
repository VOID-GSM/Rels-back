package com.example.rels.domain.lecture.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.lecture.dto.request.AttendanceUpdateRequest;
import com.example.rels.domain.lecture.dto.response.LectureAttendanceResponse;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.user.entity.Role;

@Service
public class LectureAttendanceService {

	private final LectureEnrollmentRepository lectureEnrollmentRepository;
	private final LectureAccessValidator accessValidator;

	public LectureAttendanceService(LectureEnrollmentRepository lectureEnrollmentRepository,
									LectureAccessValidator accessValidator) {
		this.lectureEnrollmentRepository = lectureEnrollmentRepository;
		this.accessValidator = accessValidator;
	}

	@Transactional(readOnly = true)
	public List<LectureAttendanceResponse> getAttendanceList(Long lectureId, Long currentUserId, Role currentUserRole) {
		LectureEntity lecture = accessValidator.requireLecture(lectureId);
		accessValidator.validateCreatorOrAdmin(lecture, currentUserId, currentUserRole);

		return lectureEnrollmentRepository.findAllByLectureId(lectureId).stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.map(e -> new LectureAttendanceResponse(
						e.getUser().getId(),
						e.getUser().getName(),
						e.getUser().getStudentNumber(),
						e.getAttendanceStatus(),
						e.getAttendedAt()
				))
				.toList();
	}

	@Transactional
	public void updateAttendances(Long lectureId, Long currentUserId, Role currentUserRole, List<AttendanceUpdateRequest> requests) {
		LectureEntity lecture = accessValidator.requireLecture(lectureId);
		accessValidator.validateCreatorOrAdmin(lecture, currentUserId, currentUserRole);

		List<Long> userIds = requests.stream().map(AttendanceUpdateRequest::userId).toList();
		Map<Long, LectureEnrollmentEntity> enrollmentMap = lectureEnrollmentRepository
				.findAllByLectureIdAndUserIdIn(lectureId, userIds).stream()
				.collect(Collectors.toMap(e -> e.getUser().getId(), Function.identity()));

		for (AttendanceUpdateRequest req : requests) {
			LectureEnrollmentEntity enrollment = enrollmentMap.get(req.userId());
			if (enrollment == null) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "수강 신청 내역이 없습니다. User ID: " + req.userId());
			}

			if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "승인된 수강생만 출석을 변경할 수 있습니다.");
			}

			enrollment.updateAttendance(req.attendanceStatus());
		}
	}
}
