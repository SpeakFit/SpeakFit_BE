package com.speakfit.backend.domain.term.service;

import com.speakfit.backend.domain.term.dto.res.TermDetailGetRes;
import com.speakfit.backend.domain.term.dto.res.TermsGetRes;
import com.speakfit.backend.domain.term.exception.TermErrorCode;
import com.speakfit.backend.domain.term.repository.TermRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
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
        var terms = termRepository.findAllByOrderByIdAsc();

        if (terms.isEmpty()){
            throw new CustomException(TermErrorCode.TERMS_EMPTY);
        }

        var items = terms.stream()
                .map(term -> TermsGetRes.TermItem.builder()
                        .termId(term.getId())
                        .title(term.getTitle())
                        .required(term.isRequired())
                        .build())
                .toList();

        return TermsGetRes.builder()
                .terms(items)
                .build();
    }

    // 약관 상세 조회 api
    @Override
    public TermDetailGetRes getTermDetail(Long termId){
        var term = termRepository.findById(termId)
                .orElseThrow(()->new CustomException(TermErrorCode.TERM_NOT_FOUND));

        return TermDetailGetRes.builder()
                .termId(term.getId())
                .title(term.getTitle())
                .content(term.getContent())
                .build();
    }
}
