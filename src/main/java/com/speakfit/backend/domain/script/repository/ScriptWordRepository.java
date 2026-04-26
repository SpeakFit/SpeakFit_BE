package com.speakfit.backend.domain.script.repository;

import com.speakfit.backend.domain.script.entity.ScriptWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptWordRepository extends JpaRepository<ScriptWord, Long> {

    @Query("select sw from ScriptWord sw join fetch sw.scriptSentence ss where ss.script.id = :scriptId order by sw.globalWordIndex asc")
    List<ScriptWord> findAllByScriptSentenceScriptIdOrderByGlobalWordIndexAsc(@Param("scriptId") Long scriptId);
}
