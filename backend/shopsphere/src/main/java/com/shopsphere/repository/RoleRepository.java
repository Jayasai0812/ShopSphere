package com.shopsphere.repository;

import com.shopsphere.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(String roleName);

    /**
     * Test/moderation helper: inserts a row into user_roles without
     * touching the lazily-loaded roles collection. Used by ReviewTest to
     * grant ADMIN to a registered user.
     */
    @Modifying
    @Query(value = "INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId)",
            nativeQuery = true)
    void linkUserToRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
