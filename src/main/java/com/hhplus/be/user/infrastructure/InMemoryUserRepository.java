package com.hhplus.be.user.infrastructure;

import com.hhplus.be.user.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> store = new ConcurrentHashMap<>();

    // 생성자에서 초기 데이터 투입
    public InMemoryUserRepository() {
        initializeData();
    }

    private void initializeData() {
        // 테스트용 사용자 5명 생성
        User user1 = User.createWithId(1L, "홍길동", "hong@example.com", 10000);
        User user2 = User.createWithId(2L, "김철수", "kim@example.com", 50000);
        User user3 = User.createWithId(3L, "이영희", "lee@example.com", 0);
        User user4 = User.createWithId(4L, "박민수", "park@example.com", 100000);
        User user5 = User.createWithId(5L, "최지훈", "choi@example.com", 250000);

        store.put(1L, user1);
        store.put(2L, user2);
        store.put(3L, user3);
        store.put(4L, user4);
        store.put(5L, user5);
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public User save(User user) {
        store.put(user.getId(), user);
        return user;
    }
}
