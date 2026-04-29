package com.speakfit.backend.global.infra.s3;

import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class S3Controller {

    private final S3Service s3Service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadFileRes> uploadFile(
            @RequestParam(defaultValue = "uploads") String type,
            @Parameter(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        String directory = resolveDirectory(type);
        String fileUrl = s3Service.upload(file, directory);

        return ApiResponse.onSuccess(
                SuccessCode.CREATED,
                UploadFileRes.builder()
                        .fileUrl(fileUrl)
                        .directory(directory)
                        .originalFileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .size(file.getSize())
                        .build()
        );
    }

    private String resolveDirectory(String type) {
        if (type == null || type.isBlank()) {
            return "uploads";
        }

        return switch (type.toLowerCase()) {
            case "audio", "voice", "record", "recording" -> "audio";
            case "document", "documents", "ppt", "pptx", "pdf" -> "documents";
            default -> "uploads";
        };
    }

    @Getter
    @Builder
    public static class UploadFileRes {
        private String fileUrl;
        private String directory;
        private String originalFileName;
        private String contentType;
        private long size;
    }
}
