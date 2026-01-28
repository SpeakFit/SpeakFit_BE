package com.speakfit.backend.domain.script.repository;

import com.speakfit.backend.domain.script.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScriptRepository extends JpaRepository<Script,Long> {
    /**
 * Finds all scripts belonging to the specified user.
 *
 * @param userId the identifier of the user whose scripts to retrieve
 * @return a list of Script objects for the given userId; empty if none found
 */
List<Script> findAllByUserId(Long userId);

}