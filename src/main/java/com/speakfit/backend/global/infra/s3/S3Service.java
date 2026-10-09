package com.speakfit.backend.global.infra.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.apiPayload.response.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
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

    /** 읽기용 서명 URL 의 유효 시간(초). 연습 화면을 오래 열어 두는 경우를 고려해 기본 1시간. */
    @Value("${app.s3.presign-expire-seconds:3600}")
    private long presignExpireSeconds = 3600;

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

    /**
     * 이 서비스가 돌려주는 오브젝트 URL(가상 호스트/경로 스타일)에서 오브젝트 키를 뽑는다.
     * http(s) URL 이 아니면 null.
     */
    public String extractObjectKey(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }

        String path = URI.create(url).getRawPath();
        if (path == null) {
            return null;
        }

        String key = URLDecoder.decode(path.replaceAll("^/+", ""), StandardCharsets.UTF_8);
        if (key.startsWith(bucket + "/")) {
            key = key.substring(bucket.length() + 1);
        }
        return key.isBlank() ? null : key;
    }

    /**
     * 비공개 버킷의 파일을 브라우저가 읽을 수 있도록 유효 시간이 있는 서명 URL 로 바꾼다.
     * <ul>
     *   <li>이 버킷의 오브젝트 URL 또는 오브젝트 키 → 서명 URL</li>
     *   <li>null/빈 값, 다른 곳의 URL → 그대로 반환</li>
     * </ul>
     * 서명은 로컬 계산이라 S3 호출이 없으며, 실패하면 원래 값을 돌려준다(응답 전체가 실패하지 않도록).
     */
    public String presignGet(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) {
            return urlOrKey;
        }

        String key;
        if (urlOrKey.startsWith("http://") || urlOrKey.startsWith("https://")) {
            if (!isOwnBucketUrl(urlOrKey)) {
                return urlOrKey;
            }
            key = extractObjectKey(urlOrKey);
        } else {
            key = urlOrKey.replaceAll("^/+", "");
        }

        if (key == null || key.isBlank()) {
            return urlOrKey;
        }

        try {
            Date expiration = new Date(System.currentTimeMillis() + presignExpireSeconds * 1000);
            return amazonS3.generatePresignedUrl(bucket, key, expiration, HttpMethod.GET).toString();
        } catch (Exception e) {
            log.warn("S3 서명 URL 생성 실패 - key: {}", key, e);
            return urlOrKey;
        }
    }

    private boolean isOwnBucketUrl(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        if (host.startsWith(bucket + ".s3")) {
            return true;
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return host.startsWith("s3") && path.startsWith("/" + bucket + "/");
    }

    /** prefix 아래의 모든 오브젝트를 삭제한다. (예: ppt/10/ — 대본 삭제, 변환 시도 정리) */
    public void deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || prefix.replace("/", "").isBlank()) {
            throw new IllegalArgumentException("S3 삭제 prefix 가 비어 있습니다.");
        }

        String normalized = prefix.replaceAll("^/+", "");
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }

        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucket)
                .withPrefix(normalized);
        ListObjectsV2Result result;
        do {
            result = amazonS3.listObjectsV2(request);
            List<DeleteObjectsRequest.KeyVersion> keys = result.getObjectSummaries().stream()
                    .map(S3ObjectSummary::getKey)
                    .map(DeleteObjectsRequest.KeyVersion::new)
                    .toList();
            if (!keys.isEmpty()) {
                amazonS3.deleteObjects(new DeleteObjectsRequest(bucket).withKeys(keys).withQuiet(true));
            }
            request.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());
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
