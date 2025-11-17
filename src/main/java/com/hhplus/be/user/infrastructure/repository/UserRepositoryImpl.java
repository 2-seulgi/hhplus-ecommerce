package com.hhplus.be.user.infrastructure.repository;

import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.infrastructure.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements com.hhplus.be.user.domain.repository.UserRepository {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toDomain);
    }

    @Override
    public User save(User user) {
        var entity = userMapper.toEntity(user);
        var savedEntity = userRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }

    @Override
    public void deleteAll() {
        userRepository.deleteAll();
    }

}
