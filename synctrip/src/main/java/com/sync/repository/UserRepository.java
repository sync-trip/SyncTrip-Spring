package com.sync.repository;

import com.sync.domain.user.OauthProvider;
import com.sync.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOauthProviderAndOauthIdAndIsDeletedFalse(OauthProvider oauthProvider, String oauthId);

    // 탈퇴 계정 포함 조회 (재가입 시 재활성화용)
    Optional<User> findByOauthProviderAndOauthId(OauthProvider oauthProvider, String oauthId);

    Optional<User> findByIdAndIsDeletedFalse(Long id);
}

