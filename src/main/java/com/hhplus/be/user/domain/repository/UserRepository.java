package com.hhplus.be.user.domain.repository;

import com.hhplus.be.user.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    // 1. 조회: ID로 사용자 찾기
    Optional<User> findById(Long id);

    // 2. 저장: 사용자 업데이트
    User save(User user);

    void deleteAll();
}
