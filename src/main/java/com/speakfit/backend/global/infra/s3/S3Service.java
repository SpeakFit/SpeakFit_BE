package com.speakfit.backend.global.infra.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.apiPayload.response.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "mp3", "wav", "m4a", "webm", "ogg", "mp4",
            "ppt", "pptx", "pdf"
    );

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file) throws IOException {
        return upload(file, "uploads");
    }

    public String upload(MultipartFile file, String directory) throws IOException {
        validateFile(file);

        String fileName = buildFileName(file.getOriginalFilename());
        String objectKey = normalizeDirectory(directory) + "/" + fileName;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            metadata.setContentType(file.getContentType());
        }

        amazonS3.putObject(bucket, objectKey, file.getInputStream(), metadata);

        return amazonS3.getUrl(bucket, objectKey).toString();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private String buildFileName(String originalFileName) {
        String cleanFileName = Paths.get(originalFileName == null ? "file" : originalFileName)
                .getFileName()
                .toString()
                .replaceAll("[^A-Za-z0-9._-]", "_");

        return UUID.randomUUID() + "_" + cleanFileName;
    }

    private String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return "uploads";
        }

        return directory
                .replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        String cleanFileName = Paths.get(fileName).getFileName().toString();
        int dotIndex = cleanFileName.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex == cleanFileName.length() - 1) {
            return "";
        }

        return cleanFileName.substring(dotIndex + 1).toLowerCase();
    }
}
