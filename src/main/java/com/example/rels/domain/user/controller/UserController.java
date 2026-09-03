package com.example.rels.domain.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.rels.domain.user.dto.UserSummaryResponse;
import com.example.rels.domain.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	/** 연사자 선택용 사용자 검색. 이름 또는 학번 일부로 찾는다. */
	@GetMapping
	public List<UserSummaryResponse> searchUsers(
			@RequestParam(name = "keyword", required = false) String keyword) {
		return userService.searchUsers(keyword);
	}
}
