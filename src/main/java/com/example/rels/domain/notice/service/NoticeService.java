package com.example.rels.domain.notice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import com.example.rels.domain.notice.dto.NoticeCreateRequest;
import com.example.rels.domain.notice.dto.NoticeDetailResponse;
import com.example.rels.domain.notice.dto.NoticeSummaryResponse;
import com.example.rels.domain.notice.dto.NoticeUpdateRequest;
import com.example.rels.domain.notice.entity.NoticeEntity;
import com.example.rels.domain.notice.repository.NoticeRepository;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    public NoticeService(NoticeRepository noticeRepository, UserRepository userRepository) {
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NoticeDetailResponse createNotice(Long userId, NoticeCreateRequest request) {
        UserEntity author = requireUser(userId);
        NoticeEntity saved = noticeRepository.save(new NoticeEntity(request.title(), request.content(), author));
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public Page<NoticeSummaryResponse> getNotices(Pageable pageable) {
        return noticeRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNoticeDetail(Long noticeId) {
        return toDetail(requireNotice(noticeId));
    }

    @Transactional
    public NoticeDetailResponse updateNotice(Long noticeId, NoticeUpdateRequest request) {
        NoticeEntity notice = requireNotice(noticeId);
        notice.update(request.title(), request.content());
        return toDetail(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        NoticeEntity notice = requireNotice(noticeId);
        noticeRepository.delete(notice);
    }

    private NoticeEntity requireNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found."));
    }

    private UserEntity requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    private NoticeSummaryResponse toSummary(NoticeEntity notice) {
        return new NoticeSummaryResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getAuthor().getId(),
                notice.getAuthor().getName(),
                notice.getCreatedAt());
    }

    private NoticeDetailResponse toDetail(NoticeEntity notice) {
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getAuthor().getId(),
                notice.getAuthor().getName(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }
}

