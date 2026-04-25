package com.speakfit.backend.domain.script.repository;

import com.speakfit.backend.domain.script.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScriptRepository extends JpaRepository<Script,Long> {
    List<Script> findAllByUserId(Long userId);

    @Query("select s from Script s join fetch s.user where s.id = :scriptId")
    Optional<Script> findByIdWithUser(@Param("scriptId") Long scriptId);
}
