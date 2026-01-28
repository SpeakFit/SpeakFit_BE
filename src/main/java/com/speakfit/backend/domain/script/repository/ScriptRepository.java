package com.speakfit.backend.domain.script.repository;

import com.speakfit.backend.domain.script.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScriptRepository extends JpaRepository<Script,Long> {
    List<Script> findAllByUserId(Long userId);

}
