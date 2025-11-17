package com.hhplus.be.user.infrastructure.repository;

import com.hhplus.be.user.infrastructure.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

}
