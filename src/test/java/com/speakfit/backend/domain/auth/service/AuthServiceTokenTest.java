package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.LoginReq;
import com.speakfit.backend.domain.auth.dto.res.LoginRes;
import com.speakfit.backend.domain.auth.entity.RefreshToken;
import com.speakfit.backend.domain.auth.exception.AuthErrorCode;
import com.speakfit.backend.domain.auth.repository.RefreshTokenRepository;
import com.speakfit.backend.domain.term.repository.TermRepository;
import com.speakfit.backend.domain.term.repository.UserTermRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.infra.jwt.JwtProvider;
import com.speakfit.backend.global.util.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 로그인 / 토큰 재발급(회전) / 로그아웃 서비스 로직 단위 테스트 (DB 불필요). */
class AuthServiceTokenTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtProvider jwtProvider =
            new JwtProvider("test-secret-key-for-auth-service-32bytes!!", 3600, 1209600);

    private AuthServiceImpl authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                mock(TermRepository.class),
                mock(UserTermRepository.class),
                refreshTokenRepository,
                passwordEncoder,
                jwtProvider);

        user = User.builder()
                .email("a@b.com").password("encoded").nickname("nick")
                .birthday("2000-01-01").gender(Gender.MALE).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // 로그인 후 DB 에 저장된 상태를 흉내 낸다.
    private RefreshToken stubStoredToken(String rawRefreshToken, Instant expiresAt) {
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHashUtil.sha256Hex(rawRefreshToken))
                .expiresAt(expiresAt)
                .build();
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(stored));
        return stored;
    }

    @Test
    @DisplayName("로그인하면 refresh 토큰은 원문이 아닌 SHA-256 해시로 저장된다")
    void loginStoresHashNotPlainToken() {
        LoginReq.Request req = new LoginReq.Request();
        ReflectionTestUtils.setField(req, "email", "a@b.com");
        ReflectionTestUtils.setField(req, "password", "pw");
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "encoded")).thenReturn(true);
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        LoginRes res = authService.login(req);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isNotEqualTo(res.getRefreshToken())
                .isEqualTo(TokenHashUtil.sha256Hex(res.getRefreshToken()));
        assertThat(jwtProvider.validateAccessToken(res.getAccessToken())).isTrue();
        assertThat(jwtProvider.validateRefreshToken(res.getRefreshToken())).isTrue();
    }

    @Test
    @DisplayName("refresh 성공 시 새 access/refresh 토큰을 발급하고 DB 의 해시를 교체(회전)한다")
    void refreshRotatesToken() throws Exception {
        String oldRefresh = jwtProvider.createRefreshToken(1L);
        RefreshToken stored = stubStoredToken(oldRefresh, Instant.now().plusSeconds(1000));
        Thread.sleep(1100); // iat(초 단위)가 달라져 새 토큰 문자열이 확실히 달라지도록

        LoginRes res = authService.refresh(oldRefresh);

        assertThat(jwtProvider.validateAccessToken(res.getAccessToken())).isTrue();
        assertThat(jwtProvider.validateRefreshToken(res.getRefreshToken())).isTrue();
        assertThat(res.getRefreshToken()).isNotEqualTo(oldRefresh);
        assertThat(stored.getTokenHash()).isEqualTo(TokenHashUtil.sha256Hex(res.getRefreshToken()));
        assertThat(res.getRefreshTokenMaxAgeSeconds()).isPositive();
    }

    @Test
    @DisplayName("쿠키가 없거나 깨진 토큰이면 거부한다")
    void refreshRejectsMissingOrGarbage() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("not-a-jwt");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("access 토큰으로는 재발급할 수 없다")
    void refreshRejectsAccessToken() {
        assertInvalid(jwtProvider.createAccessToken(1L, "a@b.com"));
    }

    @Test
    @DisplayName("DB 에 저장된 토큰이 없으면 거부한다")
    void refreshRejectsWhenNothingStored() {
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());
        assertInvalid(jwtProvider.createRefreshToken(1L));
    }

    @Test
    @DisplayName("이미 회전된(폐기된) 토큰을 다시 쓰면 거부하고 저장된 세션을 삭제한다")
    void refreshReuseRevokesSession() {
        String current = jwtProvider.createRefreshToken(1L);
        RefreshToken stored = stubStoredToken(current, Instant.now().plusSeconds(1000));
        String oldRefresh = refreshTokenDifferentFrom(current);

        assertInvalid(oldRefresh);

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    @DisplayName("DB 상 만료된 토큰이면 거부하고 삭제한다")
    void refreshRejectsExpiredStoredToken() {
        String token = jwtProvider.createRefreshToken(1L);
        RefreshToken stored = stubStoredToken(token, Instant.now().minusSeconds(1));

        assertInvalid(token);

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    @DisplayName("로그아웃은 유효한 refresh 토큰이면 삭제하고, 아니면 아무 일도 하지 않는다(멱등)")
    void logoutIsIdempotent() {
        authService.logout(jwtProvider.createRefreshToken(1L));
        verify(refreshTokenRepository).deleteByUserId(1L);

        authService.logout(null);
        authService.logout("garbage");
        authService.logout(jwtProvider.createAccessToken(1L, "a@b.com"));
        verify(refreshTokenRepository).deleteByUserId(anyLong()); // 위의 1회 호출뿐
    }

    // 서명은 유효하지만 저장된 토큰과는 다른 refresh 토큰(= 이미 회전되어 폐기된 이전 토큰)을 만든다.
    private String refreshTokenDifferentFrom(String current) {
        JwtProvider sameKeyDifferentExp =
                new JwtProvider("test-secret-key-for-auth-service-32bytes!!", 3600, 1209601);
        String other = sameKeyDifferentExp.createRefreshToken(1L); // exp 가 달라 문자열이 다르다
        assertThat(other).isNotEqualTo(current);
        return other;
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
