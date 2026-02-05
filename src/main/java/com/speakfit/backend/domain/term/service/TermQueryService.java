package com.speakfit.backend.domain.term.service;

import com.speakfit.backend.domain.term.dto.res.TermDetailGetRes;
import com.speakfit.backend.domain.term.dto.res.TermsGetRes;

public interface TermQueryService {
    TermsGetRes getTerms();

    TermDetailGetRes getTermDetail(Long termId);
}
