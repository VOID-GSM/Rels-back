package com.example.rels.notice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.rels.notice.entity.NoticeEntity;

public interface NoticeRepository extends JpaRepository<NoticeEntity, Long> {

    @EntityGraph(attributePaths = "author")
    Page<NoticeEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

