package com.hhplus.be.user.infrastructure.repository;

import com.hhplus.be.user.infrastructure.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {

}
