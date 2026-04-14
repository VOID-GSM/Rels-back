package com.example.rels.lecture.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.auth.domain.user.entity.Role;
import com.example.rels.auth.domain.user.entity.UserEntity;
import com.example.rels.auth.domain.user.repository.UserRepository;
import com.example.rels.lecture.dto.EnrollmentApplicantResponse;
import com.example.rels.lecture.dto.EnrollmentResponse;
import com.example.rels.lecture.dto.LectureAdminDetailsRequest;
import com.example.rels.lecture.dto.LectureCreateRequest;
import com.example.rels.lecture.dto.LectureDetailResponse;
import com.example.rels.lecture.dto.LectureEnrollmentListResponse;
import com.example.rels.lecture.dto.LectureSummaryResponse;
import com.example.rels.lecture.dto.LectureUpdateRequest;
import com.example.rels.lecture.entity.EnrollmentStatus;
import com.example.rels.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.lecture.entity.LectureEntity;
import com.example.rels.lecture.entity.LectureStatus;
import com.example.rels.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.lecture.repository.LectureRepository;

@Service
public class LectureService {

	private static final long CONFIRM_THRESHOLD = 10;
	private static final long MAX_CAPACITY = 30;

	private final LectureRepository lectureRepository;
	private final LectureEnrollmentRepository lectureEnrollmentRepository;
	private final UserRepository userRepository;

	public LectureService(LectureRepository lectureRepository,
			LectureEnrollmentRepository lectureEnrollmentRepository,
			UserRepository userRepository) {
		this.lectureRepository = lectureRepository;
		this.lectureEnrollmentRepository = lectureEnrollmentRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public LectureDetailResponse createLecture(Long userId, LectureCreateRequest request) {
		UserEntity creator = requireUser(userId);
		LectureEntity lecture = lectureRepository.save(new LectureEntity(request.title(), request.description(), creator));
		return toLectureDetail(lecture, userId);
	}

	@Transactional(readOnly = true)
	public Page<LectureSummaryResponse> getLectures(Pageable pageable) {
		Page<LectureEntity> lectures = lectureRepository.findAllByOrderByCreatedAtDesc(pageable);
		Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId = getEnrollmentCountsByLectureIds(lectures.getContent());

		return lectures.map(lecture -> toLectureSummary(lecture, enrollmentCountsByLectureId));
	}

	@Transactional(readOnly = true)
	public LectureDetailResponse getLectureDetail(Long lectureId, Long userId) {
		LectureEntity lecture = requireLecture(lectureId);
		return toLectureDetail(lecture, userId);
	}

	@Transactional
	public LectureDetailResponse updateLecture(Long lectureId, Long userId, LectureUpdateRequest request) {
		LectureEntity lecture = requireLecture(lectureId);
		validateCreator(lecture, userId);
		lecture.updateBasicInfo(request.title(), request.description());
		return toLectureDetail(lecture, userId);
	}

	@Transactional
	public void deleteLecture(Long lectureId, Long userId) {
		LectureEntity lecture = requireLecture(lectureId);
		validateCreator(lecture, userId);
		lectureRepository.delete(lecture);
	}

	@Transactional
	public LectureDetailResponse updateAdminDetails(Long lectureId, Long userId,
			LectureAdminDetailsRequest request) {

		LectureEntity lecture = requireLecture(lectureId);
		if (lecture.getStatus() != LectureStatus.CONFIRMED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "강의가 확정된 후에만 세부 정보를 설정할 수 있습니다.");
		}

		lecture.updateAdminDetails(request.lectureLocation(), request.lectureDate(), request.lectureTime());
		return toLectureDetail(lecture, userId);
	}

	@Transactional
	public EnrollmentResponse enroll(Long lectureId, Long userId) {
		LectureEntity lecture = requireLectureForUpdate(lectureId);
		UserEntity user = requireUser(userId);

		lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.ifPresent(existing -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 강의입니다.");
				});

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		EnrollmentStatus status = enrolledCount >= MAX_CAPACITY ? EnrollmentStatus.WAITING : EnrollmentStatus.ENROLLED;
		lectureEnrollmentRepository.save(new LectureEnrollmentEntity(lecture, user, status));

		if (status == EnrollmentStatus.ENROLLED && lecture.getStatus() == LectureStatus.OPEN
				&& enrolledCount + 1 >= CONFIRM_THRESHOLD) {
			lecture.confirm();
		}

		long nextEnrolledCount = status == EnrollmentStatus.ENROLLED ? enrolledCount + 1 : enrolledCount;
		long nextWaitingCount = status == EnrollmentStatus.WAITING ? waitingCount + 1 : waitingCount;

		return new EnrollmentResponse(lectureId, status.name(), nextEnrolledCount, nextWaitingCount);
	}

	@Transactional
	public EnrollmentResponse cancelEnrollment(Long lectureId, Long userId) {
		LectureEntity lecture = requireLectureForUpdate(lectureId);
		LectureEnrollmentEntity enrollment = lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역이 없습니다."));

		EnrollmentStatus canceledStatus = enrollment.getStatus();
		lectureEnrollmentRepository.delete(enrollment);

		if (canceledStatus == EnrollmentStatus.ENROLLED) {
			promoteFirstWaitingUser(lectureId);
		}

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		return new EnrollmentResponse(lecture.getId(), "CANCELED", enrolledCount, waitingCount);
	}

	@Transactional(readOnly = true)
	public LectureEnrollmentListResponse getEnrollmentLists(Long lectureId, Long userId, Role role) {
		LectureEntity lecture = requireLecture(lectureId);
		validateEnrollmentViewer(lecture, userId, role);

		List<LectureEnrollmentEntity> enrollments = lectureEnrollmentRepository.findByLectureIdOrderByRequestedAtAscIdAsc(lectureId);
		List<EnrollmentApplicantResponse> enrolledApplicants = enrollments.stream()
				.filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ENROLLED)
				.map(this::toEnrollmentApplicant)
				.toList();
		List<EnrollmentApplicantResponse> waitingApplicants = enrollments.stream()
				.filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.WAITING)
				.map(this::toEnrollmentApplicant)
				.toList();

		return new LectureEnrollmentListResponse(lecture.getId(), enrolledApplicants, waitingApplicants);
	}

	private void promoteFirstWaitingUser(Long lectureId) {
		lectureEnrollmentRepository.findFirstByLectureIdAndStatusOrderByRequestedAtAscIdAsc(lectureId,
				EnrollmentStatus.WAITING)
				.ifPresent(LectureEnrollmentEntity::promoteToEnrolled);
	}

	private EnrollmentApplicantResponse toEnrollmentApplicant(LectureEnrollmentEntity enrollment) {
		UserEntity user = enrollment.getUser();
		return new EnrollmentApplicantResponse(
				user.getId(),
				user.getName(),
				user.getStudentNumber(),
				enrollment.getRequestedAt());
	}

	private LectureSummaryResponse toLectureSummary(LectureEntity lecture,
			Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId) {
		long enrolledCount = getEnrollmentCount(enrollmentCountsByLectureId, lecture.getId(), EnrollmentStatus.ENROLLED);
		long waitingCount = getEnrollmentCount(enrollmentCountsByLectureId, lecture.getId(), EnrollmentStatus.WAITING);

		return new LectureSummaryResponse(
				lecture.getId(),
				lecture.getTitle(),
				lecture.getDescription(),
				lecture.getCreator().getId(),
				lecture.getCreator().getName(),
				lecture.getStatus().name(),
				enrolledCount,
				waitingCount,
				lecture.getLectureLocation(),
				lecture.getLectureDate(),
				lecture.getLectureTime(),
				lecture.getCreatedAt());
	}

	private Map<Long, Map<EnrollmentStatus, Long>> getEnrollmentCountsByLectureIds(List<LectureEntity> lectures) {
		if (lectures.isEmpty()) {
			return Map.of();
		}

		List<Long> lectureIds = lectures.stream()
				.map(LectureEntity::getId)
				.toList();

		return lectureEnrollmentRepository.countEnrollmentsByLectureIds(lectureIds).stream()
				.collect(Collectors.groupingBy(
					LectureEnrollmentCountProjection::getLectureId,
					Collectors.toMap(
						LectureEnrollmentCountProjection::getStatus,
						LectureEnrollmentCountProjection::getEnrollmentCount)));
	}

	private long getEnrollmentCount(Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId,
			Long lectureId, EnrollmentStatus status) {
		return enrollmentCountsByLectureId.getOrDefault(lectureId, Map.of())
				.getOrDefault(status, 0L);
	}

	private LectureDetailResponse toLectureDetail(LectureEntity lecture, Long userId) {
		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lecture.getId(), EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lecture.getId(), EnrollmentStatus.WAITING);

		String myEnrollmentStatus = lectureEnrollmentRepository.findByLectureIdAndUserId(lecture.getId(), userId)
				.map(enrollment -> enrollment.getStatus().name())
				.orElse(null);

		return new LectureDetailResponse(
				lecture.getId(),
				lecture.getTitle(),
				lecture.getDescription(),
				lecture.getCreator().getId(),
				lecture.getCreator().getName(),
				lecture.getStatus().name(),
				enrolledCount,
				waitingCount,
				myEnrollmentStatus,
				lecture.getLectureLocation(),
				lecture.getLectureDate(),
				lecture.getLectureTime(),
				lecture.getCreatedAt());
	}

	private UserEntity requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
	}

	private LectureEntity requireLecture(Long lectureId) {
		return lectureRepository.findById(lectureId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."));
	}

	private LectureEntity requireLectureForUpdate(Long lectureId) {
		return lectureRepository.findByIdForUpdate(lectureId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."));
	}

	private void validateCreator(LectureEntity lecture, Long userId) {
		if (!lecture.getCreator().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "강의 작성자만 수정 또는 삭제할 수 있습니다.");
		}
	}

	private void validateEnrollmentViewer(LectureEntity lecture, Long userId, Role role) {
		if (role == Role.ADMIN) {
			return;
		}
		if (!lecture.getCreator().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "강의 신청자 목록은 작성자 또는 관리자만 조회할 수 있습니다.");
		}
	}
}

