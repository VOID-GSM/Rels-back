package com.example.rels.domain.lecture.controller;

import com.example.rels.domain.auth.dto.MyLecturesResponse;
import com.example.rels.domain.lecture.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getLectures(pageable, authenticatedUser.userId(), authenticatedUser.role());
	}

	@GetMapping("/discord")
	public Page<LectureSummaryResponse> getLecturesForDiscord(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return lectureService.getLectures(pageable, null, null);
	}

	@GetMapping("/{lectureId}")
	public LectureDetailResponse getLectureDetail(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getLectureDetail(lectureId, authenticatedUser.userId(), authenticatedUser.role());
	}

	// Public endpoint for Discord bot usage - does not require authentication
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

	@PostMapping("/{lectureId}/approval")
	@PreAuthorize("hasRole('ADMIN')")
	public LectureDetailResponse approveLecture(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.approveLecture(lectureId, authenticatedUser.userId());
	}

	@PostMapping("/{lectureId}/rejection")
	@PreAuthorize("hasRole('ADMIN')")
	public LectureDetailResponse rejectLecture(
			@PathVariable Long lectureId,
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody LectureRejectRequest request) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.rejectLecture(lectureId, authenticatedUser.userId(), request.rejectionReason());
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
	public EnrollmentListResponse getEnrollments(@PathVariable Long lectureId) {
		return lectureService.getEnrollments(lectureId);
	}

	@GetMapping("/enrollments/me")
	public MyLecturesResponse getMyLectures(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		AuthenticatedUser authenticatedUser = requireUser(currentUser);
		return lectureService.getMyLectures(authenticatedUser.userId());
	}

	private AuthenticatedUser requireUser(AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 필요합니다.");
		}
		return currentUser;
	}
}

