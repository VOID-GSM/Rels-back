package com.example.rels.domain.lecture.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.global.security.AuthenticatedUser;
import com.example.rels.domain.lecture.dto.EnrollmentResponse;
import com.example.rels.domain.lecture.dto.LectureAdminDetailsRequest;
import com.example.rels.domain.lecture.dto.LectureCreateRequest;
import com.example.rels.domain.lecture.dto.LectureDetailResponse;
import com.example.rels.domain.lecture.dto.LectureSummaryResponse;
import com.example.rels.domain.lecture.dto.LectureUpdateRequest;
import com.example.rels.domain.lecture.service.LectureService;

import jakarta.validation.Valid;

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
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return lectureService.getLectures(pageable);
	}

	@GetMapping("/{lectureId}")
	public LectureDetailResponse getLectureDetail(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getLectureDetail(lectureId, authenticatedUser.userId());
	}

	@PatchMapping("/{lectureId}")
	public LectureDetailResponse updateLecture(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody LectureUpdateRequest request) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.updateLecture(lectureId, authenticatedUser.userId(), request);
	}

	@DeleteMapping("/{lectureId}")
	public ResponseEntity<Void> deleteLecture(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		lectureService.deleteLecture(lectureId, authenticatedUser.userId());
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

	@PatchMapping("/{lectureId}/admin-details")
	@PreAuthorize("hasRole('ADMIN')")
	public LectureDetailResponse updateAdminDetails(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody LectureAdminDetailsRequest request) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.updateAdminDetails(lectureId, authenticatedUser.userId(), request);
	}

	private AuthenticatedUser requireUser(AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 필요합니다.");
		}
		return currentUser;
	}
}

