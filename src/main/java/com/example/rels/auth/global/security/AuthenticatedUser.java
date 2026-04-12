package com.example.rels.auth.global.security;

import com.example.rels.auth.domain.user.entity.Role;

public record AuthenticatedUser(Long userId, String email, String name, String studentNumber, Role role) {
}

