package com.sync.repository;

import com.sync.domain.user.OauthProvider;
import com.sync.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOauthProviderAndOauthIdAndIsDeletedFalse(OauthProvider oauthProvider, String oauthId);

    Optional<User> findByIdAndIsDeletedFalse(Long id);
}

