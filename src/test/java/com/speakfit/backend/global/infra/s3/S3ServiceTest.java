package com.speakfit.backend.global.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ServiceTest {

    private static final String BUCKET = "speakfit-prod-1";

    private final S3Client s3Client = mock(S3Client.class);
    private S3Service service;

    @BeforeEach
    void setUp() {
        service = new S3Service(s3Client, mock(S3Presigner.class));
        ReflectionTestUtils.setField(service, "bucket", BUCKET);
    }

    private static ListObjectsV2Response page(boolean truncated, String token, String... keys) {
        return ListObjectsV2Response.builder()
                .contents(java.util.Arrays.stream(keys).map(key -> S3Object.builder().key(key).build()).toList())
                .isTruncated(truncated)
                .nextContinuationToken(token)
                .build();
    }

    @Test
    void 가상_호스트_URL_에서_키를_뽑는다() {
        assertThat(service.extractObjectKey(
                "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/a/1_x.pptx"))
                .isEqualTo("ppt/10/attempts/a/1_x.pptx");
    }

    @Test
    void 경로_스타일_URL_에서_버킷_이름을_제거한다() {
        assertThat(service.extractObjectKey(
                "https://s3.ap-northeast-2.amazonaws.com/speakfit-prod-1/ppt/10/attempts/a/x.pdf"))
                .isEqualTo("ppt/10/attempts/a/x.pdf");
    }

    @Test
    void 인코딩된_키는_디코딩하고_URL_이_아니면_null() {
        assertThat(service.extractObjectKey("https://b.s3.amazonaws.com/ppt/1/attempts/a/x%20y.pptx"))
                .isEqualTo("ppt/1/attempts/a/x y.pptx");
        assertThat(service.extractObjectKey(null)).isNull();
        assertThat(service.extractObjectKey("uploads/ppt/1/file.pptx")).isNull();
    }

    @Test
    void prefix_아래_오브젝트를_페이지_단위로_모두_삭제한다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(true, "t1", "ppt/10/a", "ppt/10/b"))
                .thenReturn(page(false, null, "ppt/10/c"));

        service.deleteByPrefix("ppt/10");

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(listCaptor.capture());
        // prefix 는 슬래시로 끝나야 다른 대본(ppt/100)을 지우지 않는다
        assertThat(listCaptor.getAllValues().get(0).prefix()).isEqualTo("ppt/10/");
        assertThat(listCaptor.getAllValues().get(0).continuationToken()).isNull();
        assertThat(listCaptor.getAllValues().get(1).continuationToken()).isEqualTo("t1");

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, times(2)).deleteObjects(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues().get(0).delete().objects()).extracting(ObjectIdentifier::key)
                .containsExactly("ppt/10/a", "ppt/10/b");
        assertThat(deleteCaptor.getAllValues().get(1).delete().objects()).extracting(ObjectIdentifier::key)
                .containsExactly("ppt/10/c");
    }

    @Test
    void 지울_것이_없으면_삭제_요청을_보내지_않는다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(false, null));

        service.deleteByPrefix("ppt/10/attempts/x");

        verify(s3Client, times(0)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void 빈_prefix_는_버킷_전체_삭제를_막기_위해_거부한다() {
        assertThatThrownBy(() -> service.deleteByPrefix("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deleteByPrefix("/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deleteByPrefix(null)).isInstanceOf(IllegalArgumentException.class);
        verify(s3Client, times(0)).listObjectsV2(any(ListObjectsV2Request.class));
    }
}
