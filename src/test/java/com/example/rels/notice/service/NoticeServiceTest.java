package com.example.rels.notice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.rels.domain.notice.service.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import com.example.rels.domain.notice.dto.NoticeCreateRequest;
import com.example.rels.domain.notice.dto.NoticeDetailResponse;
import com.example.rels.domain.notice.dto.NoticeSummaryResponse;
import com.example.rels.domain.notice.dto.NoticeUpdateRequest;
import com.example.rels.domain.notice.entity.NoticeEntity;
import com.example.rels.domain.notice.repository.NoticeRepository;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private UserRepository userRepository;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeRepository, userRepository);
    }

    @Test
    void createNoticeSavesTitleAndContent() {
        UserEntity admin = new UserEntity("admin@test.com", "admin", "1000000000", Role.ADMIN);
        setId(admin, 10L);

        when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(noticeRepository.save(org.mockito.Mockito.any(NoticeEntity.class)))
                .thenAnswer(invocation -> {
                    NoticeEntity notice = invocation.getArgument(0);
                    setId(notice, 1L);
                    setCreatedAt(notice, LocalDateTime.of(2026, 4, 13, 1, 0));
                    setUpdatedAt(notice, LocalDateTime.of(2026, 4, 13, 1, 0));
                    return notice;
                });

        NoticeDetailResponse response = noticeService.createNotice(10L, new NoticeCreateRequest("notice", "content"));

        ArgumentCaptor<NoticeEntity> captor = ArgumentCaptor.forClass(NoticeEntity.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("notice", captor.getValue().getTitle());
        assertEquals("content", captor.getValue().getContent());
        assertEquals(1L, response.id());
        assertEquals("admin", response.authorName());
    }

    @Test
    void getNoticesReturnsPagedSummary() {
        UserEntity admin = new UserEntity("admin@test.com", "admin", "1000000000", Role.ADMIN);
        setId(admin, 10L);

        NoticeEntity notice = new NoticeEntity("notice", "content", admin);
        setId(notice, 1L);
        setCreatedAt(notice, LocalDateTime.of(2026, 4, 13, 1, 0));

        Pageable pageable = PageRequest.of(0, 20);
        when(noticeRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(notice), pageable, 1));

        Page<NoticeSummaryResponse> page = noticeService.getNotices(pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals("notice", page.getContent().get(0).title());
        assertEquals("admin", page.getContent().get(0).authorName());
    }

    @Test
    void updateNoticeThrowsWhenNotFound() {
        when(noticeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> noticeService.updateNotice(999L, new NoticeUpdateRequest("update", "updated-content")));
    }

    private void setId(NoticeEntity notice, Long id) {
        try {
            Field field = NoticeEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(notice, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to set id", e);
        }
    }

    private void setId(UserEntity user, Long id) {
        try {
            Field field = UserEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to set id", e);
        }
    }

    private void setCreatedAt(NoticeEntity notice, LocalDateTime createdAt) {
        try {
            Field field = NoticeEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(notice, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to set createdAt", e);
        }
    }

    private void setUpdatedAt(NoticeEntity notice, LocalDateTime updatedAt) {
        try {
            Field field = NoticeEntity.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(notice, updatedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to set updatedAt", e);
        }
    }
}

