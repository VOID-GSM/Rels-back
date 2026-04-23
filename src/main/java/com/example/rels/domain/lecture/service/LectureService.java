package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.rels.domain.lecture.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;

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
    if (request.capacityByGrade() != null && !request.capacityByGrade().isEmpty() && request.totalCapacity() != null && request.totalCapacity() > 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원과 전체 정원은 동시에 설정할 수 없습니다.");
    }
    UserEntity creator = requireUser(userId);
    LectureEntity lecture = new LectureEntity(
        request.title(),
        request.description(),
        creator,
        request.lectureLocation(),
        request.lectureDate(),
        request.lectureTime(),
        request.applicationDeadline(),
        request.totalCapacity()
    );
    lecture.setCapacityByGrade(request.capacityByGrade());
    lecture = lectureRepository.save(lecture);
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
    if (request.capacityByGrade() != null && !request.capacityByGrade().isEmpty() && request.totalCapacity() != null && request.totalCapacity() > 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원과 전체 정원은 동시에 설정할 수 없습니다.");
    }
    LectureEntity lecture = requireLecture(lectureId);
    validateCreator(lecture, userId);
    lecture.updateAllDetails(
        request.title(),
        request.description(),
        request.capacityByGrade(),
        request.totalCapacity(),
        request.lectureLocation(),
        request.lectureDate(),
        request.lectureTime(),
        request.applicationDeadline()
    );
    return toLectureDetail(lecture, userId);
	   }

	@Transactional
	public void deleteLecture(Long lectureId, Long userId) {
		LectureEntity lecture = requireLecture(lectureId);
		validateCreator(lecture, userId);
		lectureRepository.delete(lecture);
	}



	@Transactional
	public EnrollmentResponse enroll(Long lectureId, Long userId) {
		LectureEntity lecture = requireLectureForUpdate(lectureId);
		if (LocalDateTime.now().isAfter(lecture.getApplicationDeadline())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "신청 마감일이 지났습니다.");
		}
		UserEntity user = requireUser(userId);

		lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.ifPresent(existing -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 강의입니다.");
				});

		Integer userGrade = extractGradeFromStudentNumber(user.getStudentNumber());
		Map<Integer, Integer> capacityByGrade = lecture.getCapacityByGrade();
		Integer totalCapacity = lecture.getTotalCapacity();
		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		boolean useGradeCapacity = capacityByGrade != null && !capacityByGrade.isEmpty() && userGrade != null && capacityByGrade.containsKey(userGrade);
		boolean isFull = false;
		if (useGradeCapacity) {
			long gradeEnrolled = lectureEnrollmentRepository.findAllByLectureId(lectureId).stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.filter(e -> {
					Integer grade = extractGradeFromStudentNumber(e.getUser().getStudentNumber());
					return grade != null && grade.equals(userGrade);
				})
				.count();
			isFull = gradeEnrolled >= capacityByGrade.get(userGrade);
		} else if (totalCapacity != null && totalCapacity > 0) {
			isFull = enrolledCount >= totalCapacity;
		} else {
			isFull = false; // 무제한
		}

		EnrollmentStatus status = isFull ? EnrollmentStatus.WAITING : EnrollmentStatus.ENROLLED;
		lectureEnrollmentRepository.save(new LectureEnrollmentEntity(lecture, user, status));

		if (status == EnrollmentStatus.ENROLLED && lecture.getStatus() == LectureStatus.OPEN
				&& enrolledCount + 1 >= CONFIRM_THRESHOLD) {
			lecture.confirm();
		}

		long nextEnrolledCount = status == EnrollmentStatus.ENROLLED ? enrolledCount + 1 : enrolledCount;
		long nextWaitingCount = status == EnrollmentStatus.WAITING ? waitingCount + 1 : waitingCount;

		return new EnrollmentResponse(lectureId, status.name(), nextEnrolledCount, nextWaitingCount);
	}

	private Integer extractGradeFromStudentNumber(String studentNumber) {
		if (studentNumber == null || studentNumber.isEmpty()) return null;
		try {
			return Integer.parseInt(studentNumber.substring(1, 2));
		} catch (Exception e) {
			return null;
		}
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

	private void promoteFirstWaitingUser(Long lectureId) {
		lectureEnrollmentRepository.findFirstByLectureIdAndStatusOrderByRequestedAtAscIdAsc(lectureId,
				EnrollmentStatus.WAITING)
				.ifPresent(LectureEnrollmentEntity::promoteToEnrolled);
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
				lecture.getCreatedAt(),
				lecture.getCapacityByGrade(),
				lecture.getTotalCapacity()
		);
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
				lecture.getCreatedAt(),
				lecture.getCapacityByGrade(),
				lecture.getTotalCapacity()
		);
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

	@Transactional
	public void setUnconfirmedIfDeadlinePassed() {
		List<LectureEntity> lectures = lectureRepository.findAll();
		for (LectureEntity lecture : lectures) {
			if (lecture.getStatus() == LectureStatus.OPEN &&
				lecture.getApplicationDeadline() != null &&
				LocalDateTime.now().isAfter(lecture.getApplicationDeadline())) {
				long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lecture.getId(), EnrollmentStatus.ENROLLED);
				if (enrolledCount < CONFIRM_THRESHOLD) {
					lecture.setStatus(LectureStatus.UNCONFIRMED);
				}
			}
		}
	}

	@Transactional(readOnly = true)
	public EnrollmentListResponse getEnrollments(Long lectureId) {
		requireLecture(lectureId);

		List<LectureEnrollmentEntity> allEnrollments = lectureEnrollmentRepository.findAllByLectureId(lectureId);

		List<EnrollmentUserResponse> enrolled = allEnrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.map(this::toEnrollmentUserResponse)
				.toList();

		List<EnrollmentUserResponse> waiting = allEnrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.WAITING)
				.map(this::toEnrollmentUserResponse)
				.toList();

		return new EnrollmentListResponse(enrolled, waiting);
	}

	private EnrollmentUserResponse toEnrollmentUserResponse(LectureEnrollmentEntity enrollment) {
		UserEntity user = enrollment.getUser();
		return new EnrollmentUserResponse(
				user.getId(),
				user.getName(),
				user.getStudentNumber()
		);
	}
}

