package com.example.rels.global.security;

import com.example.rels.domain.user.entity.Role;

public record AuthenticatedUser(Long userId, String email, String name, String studentNumber, Role role) {
}

