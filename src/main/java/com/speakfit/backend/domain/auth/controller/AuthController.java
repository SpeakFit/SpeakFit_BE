package com.speakfit.backend.domain.auth.controller;

import com.speakfit.backend.domain.auth.dto.req.LoginReq;
import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.LoginRes;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;
import com.speakfit.backend.domain.auth.service.AuthService;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    // 회원가입
    @PostMapping("/signup")
    public ApiResponse<SignUpRes> signUp(@RequestBody @Valid SignUpReq.Request request) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, authService.signUp(request));
    }

    // 로그인
    @PostMapping("/login")
    public ApiResponse<LoginRes> login(@RequestBody @Valid LoginReq.Request request,
                                        HttpServletResponse response){
        LoginRes loginRes = authService.login(request);

        cookieUtil.addRefreshTokenCookie(
                response,
                loginRes.getRefreshToken(),
                loginRes.getRefreshTokenMaxAgeSeconds()
        );

        return ApiResponse.onSuccess(SuccessCode.OK, loginRes);
    }

    // 토큰 재발급 (쿠키의 refreshToken 사용, 성공 시 refresh 토큰도 새로 내려준다)
    @PostMapping("/refresh")
    public ApiResponse<LoginRes> refresh(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            LoginRes loginRes = authService.refresh(refreshToken);

            cookieUtil.addRefreshTokenCookie(
                    response,
                    loginRes.getRefreshToken(),
                    loginRes.getRefreshTokenMaxAgeSeconds()
            );

            return ApiResponse.onSuccess(SuccessCode.OK, loginRes);
        } catch (CustomException e) {
            // 쓸 수 없는 쿠키는 브라우저에 남지 않도록 제거한다.
            cookieUtil.clearRefreshTokenCookie(response);
            throw e;
        }
    }

    // 로그아웃 (access 토큰이 만료된 상태에서도 가능해야 하므로 쿠키 기반, 멱등)
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        cookieUtil.clearRefreshTokenCookie(response);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }
}
