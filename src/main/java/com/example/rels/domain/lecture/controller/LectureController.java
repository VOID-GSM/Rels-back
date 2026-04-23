package com.example.rels.domain.lecture.controller;

import com.example.rels.domain.lecture.dto.*;
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

	@GetMapping("/{lectureId}/enrollments")
	public EnrollmentListResponse getEnrollments(@PathVariable Long lectureId) {
		return lectureService.getEnrollments(lectureId);
	}


	private AuthenticatedUser requireUser(AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 필요합니다.");
		}
		return currentUser;
	}
}

