package com.example.rels.domain.notice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.example.rels.domain.notice.dto.NoticeCreateRequest;
import com.example.rels.domain.notice.dto.NoticeDetailResponse;
import com.example.rels.domain.notice.dto.NoticeSummaryResponse;
import com.example.rels.domain.notice.dto.NoticeUpdateRequest;
import com.example.rels.domain.notice.service.NoticeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NoticeDetailResponse> createNotice(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody NoticeCreateRequest request) {
        AuthenticatedUser authenticatedUser = requireUser(currentUser);
        NoticeDetailResponse response = noticeService.createNotice(authenticatedUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public Page<NoticeSummaryResponse> getNotices(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return noticeService.getNotices(pageable);
    }

    @GetMapping("/{noticeId}")
    public NoticeDetailResponse getNoticeDetail(
            @PathVariable Long noticeId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        requireUser(currentUser);
        return noticeService.getNoticeDetail(noticeId);
    }

    @PatchMapping("/{noticeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public NoticeDetailResponse updateNotice(
            @PathVariable Long noticeId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody NoticeUpdateRequest request) {
        requireUser(currentUser);
        return noticeService.updateNotice(noticeId, request);
    }

    @DeleteMapping("/{noticeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNotice(
            @PathVariable Long noticeId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        requireUser(currentUser);
        noticeService.deleteNotice(noticeId);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser requireUser(AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return currentUser;
    }
}

