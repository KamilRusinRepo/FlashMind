package com.example.backend.role;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from Role role where role.name = :name")
    Optional<Role> findByNameForUpdate(@Param("name") RoleName name);
}
