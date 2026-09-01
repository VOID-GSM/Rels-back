package com.example.rels.domain.lecture.controller;

import com.example.rels.domain.lecture.dto.request.AttendanceUpdateRequest;
import com.example.rels.domain.lecture.dto.request.EnrollmentDecisionRequest;
import com.example.rels.domain.lecture.dto.request.LectureApprovalRequest;
import com.example.rels.domain.lecture.dto.request.LectureCreateRequest;
import com.example.rels.domain.lecture.dto.request.LectureUpdateRequest;
import com.example.rels.domain.lecture.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.global.security.AuthenticatedUser;
import com.example.rels.domain.lecture.service.LectureService;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/lectures")
public class LectureController {

	private final LectureService lectureService;

	public LectureController(LectureService lectureService) {
		this.lectureService = lectureService;
	}

	@PostMapping
	public ResponseEntity<LectureDetailResponse> createLecture(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody LectureCreateRequest request) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		LectureDetailResponse response = lectureService.createLecture(authenticatedUser.userId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public Page<LectureSummaryResponse> getLectures(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getLectures(pageable, authenticatedUser.userId());
	}

	@GetMapping("/pending")
	public Page<LectureSummaryResponse> getPendingLectures(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getPendingLectures(authenticatedUser.role(), pageable);
	}

	@PatchMapping("/{lectureId}/approval")
	public ResponseEntity<Void> updateLectureApproval(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody LectureApprovalRequest request) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		lectureService.updateLectureApproval(lectureId, authenticatedUser.role(), request);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/discord")
	public Page<LectureSummaryResponse> getLecturesForDiscord(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return lectureService.getLectures(pageable, null);
	}

	@GetMapping("/{lectureId}")
	public LectureDetailResponse getLectureDetail(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getLectureDetail(lectureId, authenticatedUser.userId(), authenticatedUser.role());
	}

	@GetMapping("/discord/{lectureId}")
	public LectureDetailResponse getLectureDetailForDiscord(
			@PathVariable Long lectureId) {
		return lectureService.getLectureDetailForDiscord(lectureId);
	}

	@PatchMapping("/{lectureId}")
	public LectureDetailResponse updateLecture(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody LectureUpdateRequest request) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.updateLecture(lectureId, authenticatedUser.userId(), authenticatedUser.role(), request);
	}

	@DeleteMapping("/{lectureId}")
	public ResponseEntity<Void> deleteLecture(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		lectureService.deleteLecture(lectureId, authenticatedUser.userId(), authenticatedUser.role());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{lectureId}/enrollments")
	public EnrollmentResponse enroll(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.enroll(lectureId, authenticatedUser.userId());
	}

	@DeleteMapping("/{lectureId}/enrollments")
	public EnrollmentResponse cancelEnrollment(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.cancelEnrollment(lectureId, authenticatedUser.userId());
	}

	@GetMapping("/{lectureId}/enrollments")
	public EnrollmentListResponse getEnrollments(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getEnrollments(lectureId, authenticatedUser.userId(), authenticatedUser.role());
	}

	@PatchMapping("/{lectureId}/enrollments/{userId}/decision")
	public EnrollmentResponse decideWaitingEnrollment(
			@PathVariable Long lectureId,
			@PathVariable Long userId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody EnrollmentDecisionRequest request) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.decideWaitingEnrollment(lectureId, userId, authenticatedUser.userId(), authenticatedUser.role(), request);
	}

	@GetMapping("/enrollments/me")
	public MyLecturesResponse getMyLectures(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getMyLectures(authenticatedUser.userId());
	}

	@GetMapping("/{lectureId}/attendances")
	public List<LectureAttendanceResponse> getAttendanceList(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getAttendanceList(lectureId, authenticatedUser.userId(), authenticatedUser.role());
	}

	@PatchMapping("/{lectureId}/attendances")
	public ResponseEntity<Void> updateAttendances(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody List<AttendanceUpdateRequest> requests) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		lectureService.updateAttendances(lectureId, authenticatedUser.userId(), authenticatedUser.role(), requests);
		return ResponseEntity.ok().build();
	}

	private AuthenticatedUser requireUser(AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 필요합니다.");
		}
		return currentUser;
	}
}
