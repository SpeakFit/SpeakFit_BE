package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.StartPracticeRes;
import com.speakfit.backend.domain.practice.dto.res.StopPracticeRes;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.PracticeStatus;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeServiceImpl implements PracticeService {

    private final PracticeRepository practiceRepository;
    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;

    // 발표 연습 시작 서비스 구현

    @Override
    @Transactional
    public StartPracticeRes startPractice(StartPracticeReq.Request req) {

        // 1. 임시 사용자 조회 (JWT 적용 전 1번 유저 고정)
        Long tempUserId = 1L;
        User user = userRepository.findById(tempUserId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        // 2. 발표 대본 조회
        Script script = scriptRepository.findById(req.getScriptId())
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        // 3. 사용자 권한 체크
        if (!script.getUser().getId().equals(tempUserId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 4. 연습 기록 생성
        PracticeRecord practiceRecord = PracticeRecord.builder()
                .user(user)
                .script(script)
                .status(PracticeStatus.RECORDING)
                .time(0.0)
                .audioUrl(null)
                .build();

        // 5. DB에 저장
        PracticeRecord savedRecord = practiceRepository.save(practiceRecord);

        // 6. Entity -> DTO 변환 및 반환
        return StartPracticeRes.builder()
                .practiceId(savedRecord.getId())
                .status(savedRecord.getStatus().toString())
                .build();

    }

    // 발표 연습 종료 서비스 구현
    @Override
    @Transactional
    public StopPracticeRes stopPractice(Long practiceId, StopPracticeReq.Request req) {
        // 1. 발표 연습 기록 조회
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        // 2. 임시 사용자 접근 권한 체크(JWT 적용 전 1번 유저 고정)
        Long currentUserId = 1L;
        if (!practiceRecord.getUser().getId().equals(currentUserId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 3. DTO에서 파일을 꺼내서 업로드
        String audioUrl = uploadAudioFile(practiceId, req.getAudio());

        // 4. db에 저장
        practiceRecord.stopPractice(audioUrl, req.getTime());

        // 5. Entity -> DTO 변환 및 반환
        return StopPracticeRes.builder()
                .practiceId(practiceRecord.getId())
                .time(practiceRecord.getTime())
                .status(practiceRecord.getStatus().toString())
                .build();
    }

    // 발표 연습 종료 음성파일 업로드 메소드 [로컬 저장 로직]
    // 추후 인프라 설계 시 수정예정
    private String uploadAudioFile(Long practiceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            // 1. 저장할 디렉토리 설정 및 생성 (Files.createDirectories 사용)
            Path uploadDirPath = Paths.get("uploads/audio/");
            if (!Files.exists(uploadDirPath)) {
                Files.createDirectories(uploadDirPath); // 생성 실패 시 자동으로 IOException 발생
            }

            // 2. 파일명 생성 로직
            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = practiceId + extension;
            Path filePath = uploadDirPath.resolve(fileName);

            // 3. 실제 파일 저장
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return filePath.toAbsolutePath().toString();

        } catch (IOException e) {
            throw new RuntimeException("로컬 파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    // 발표 연습 종료 음성파일 업로드 메소드 파일 확장자 추출 로직 분리
    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return "";
    }
}
