package com.speakfit.backend.domain.auth.controller;

import com.speakfit.backend.domain.auth.dto.req.LoginReq;
import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.LoginRes;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;
import com.speakfit.backend.domain.auth.service.AuthService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
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
                loginRes.getRefreshTokenMaxAgeSeconds(),
                false
        );

        return ApiResponse.onSuccess(SuccessCode.OK, loginRes);
    }
}
