package com.speakfit.backend.domain.script.service;

import java.nio.file.Path;

public interface PptConvertAsyncService {

    void convertPptAsync(Long scriptId, Long userId, String sourcePptPath, Path uploadDirPath, String previousPptUrl);
}
