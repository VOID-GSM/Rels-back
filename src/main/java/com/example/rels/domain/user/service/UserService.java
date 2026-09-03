package com.example.rels.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rels.domain.user.dto.UserSummaryResponse;
import com.example.rels.domain.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * 연사자를 고르려면 이름이나 학번으로 사람을 찾아야 한다.
	 * 검색어가 비면 전교생 명부가 그대로 흘러나오므로 빈 목록을 돌려준다.
	 */
	@Transactional(readOnly = true)
	public List<UserSummaryResponse> searchUsers(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return List.of();
		}

		String trimmed = keyword.trim();

		return userRepository
				.findTop20ByNameContainingIgnoreCaseOrStudentNumberContainingOrderByStudentNumberAsc(trimmed, trimmed)
				.stream()
				.map(user -> new UserSummaryResponse(user.getId(), user.getName(), user.getStudentNumber()))
				.toList();
	}
}
