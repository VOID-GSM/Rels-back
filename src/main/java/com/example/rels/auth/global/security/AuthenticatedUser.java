package com.example.rels.auth.global.security;

public record AuthenticatedUser(Long userId, String email, String name, String studentNumber, String role) {
}

