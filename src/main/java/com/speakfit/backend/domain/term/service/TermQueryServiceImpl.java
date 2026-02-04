package com.speakfit.backend.domain.term.service;

import com.speakfit.backend.domain.term.dto.res.TermsGetRes;
import com.speakfit.backend.domain.term.repository.TermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermQueryServiceImpl implements TermQueryService {

    private final TermRepository termRepository;

    // 약관 목록 조회 api
    @Override
    public TermsGetRes getTerms(){
        var items = termRepository.findAllByOrderByIdAsc().stream()
                .map(term -> TermsGetRes.TermItem.builder()
                        .termId(term.getId())
                        .title(term.getTitle())
                        .required(term.isRequired())
                        .build())
                .collect(Collectors.toList());

        return TermsGetRes.builder()
                .terms(items)
                .build();
    }
}
