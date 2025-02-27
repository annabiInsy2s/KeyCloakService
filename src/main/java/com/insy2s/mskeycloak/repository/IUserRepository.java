package com.insy2s.mskeycloak.repository;

import com.insy2s.mskeycloak.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link User} entity.
 */
public interface IUserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

}
