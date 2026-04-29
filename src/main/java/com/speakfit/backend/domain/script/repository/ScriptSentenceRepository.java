package com.speakfit.backend.domain.script.repository;

import com.speakfit.backend.domain.script.entity.ScriptSentence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptSentenceRepository extends JpaRepository<ScriptSentence, Long> {

    @Query("select distinct ss from ScriptSentence ss left join fetch ss.scriptWords where ss.script.id = :scriptId order by ss.sentenceIndex asc")
    List<ScriptSentence> findAllByScriptIdOrderBySentenceIndexAsc(@Param("scriptId") Long scriptId);
}
