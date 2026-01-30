package com.speakfit.backend.global.infra.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

@Component
public class PythonServerRunner implements ApplicationRunner {

    private Process pythonProcess;

    // 경로 상수 정의
    private static final String PY_DIR = "python-analysis-server";
    private static final String PY_SCRIPT = "main.py";
    private static final String REQUIREMENTS = "requirements.txt";
    private static final String VENV_DIR = "venv";

    @Override
    public void run(ApplicationArguments args) {
        // 1. 가상환경(venv) 자동 생성
        createVirtualEnvIfNotExist();

        // 2. 라이브러리 자동 설치
        installDependencies();

        // 3. 파이썬 서버 실행
        startPythonServer();
    }

    // [1단계] 가상환경 생성
    private void createVirtualEnvIfNotExist() {
        File venvFolder = new File(PY_DIR, VENV_DIR);
        if (venvFolder.exists()) {
            return; // 이미 있으면 통과
        }

        System.out.println("[Python] 가상환경(venv) 생성 중... (시간이 좀 걸릴 수 있습니다)");
        try {
            // python -m venv venv
            ProcessBuilder pb = new ProcessBuilder("python", "-m", "venv", VENV_DIR);
            pb.directory(new File(PY_DIR));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("[Python] 가상환경 생성 완료.");
            } else {
                System.err.println("[Python] 가상환경 생성 실패 (Exit Code: " + exitCode + ")");
            }
        } catch (Exception e) {
            System.err.println("[Python] 가상환경 생성 중 오류: " + e.getMessage());
        }
    }

    // [2단계] 라이브러리 설치
    private void installDependencies() {
        try {
            // requirements.txt가 없으면 패스
            if (!new File(PY_DIR, REQUIREMENTS).exists()) return;

            System.out.println("[Python] 라이브러리 설치 확인 중...");

            // 생성된 가상환경 파이썬 사용
            String pythonExe = getPythonExecutable();

            // pip install -r requirements.txt
            ProcessBuilder pb = new ProcessBuilder(pythonExe, "-m", "pip", "install", "-r", REQUIREMENTS);
            pb.directory(new File(PY_DIR));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 설치 로그 읽기 (별도 스레드)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 필요한 경우 로그 출력 가능
                    }
                } catch (IOException e) {}
            }).start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("[Python] 라이브러리 준비 완료.");
            } else {
                System.err.println("[Python] 라이브러리 설치 중 경고 발생.");
            }
        } catch (Exception e) {
            System.err.println("[Python] 라이브러리 설치 실패: " + e.getMessage());
        }
    }

    // [3단계] 서버 실행
    private void startPythonServer() {
        try {
            File scriptFile = new File(PY_DIR, PY_SCRIPT);
            if (!scriptFile.exists()) {
                System.err.println("[Python] 실행 파일 없음: " + scriptFile.getAbsolutePath());
                return;
            }

            String pythonExe = getPythonExecutable();
            System.out.println("[Python] 분석 서버 실행 중... (" + pythonExe + ")");

            ProcessBuilder pb = new ProcessBuilder(pythonExe, PY_SCRIPT);
            pb.directory(new File(PY_DIR));
            pb.redirectErrorStream(true);

            this.pythonProcess = pb.start();

            // 파이썬 서버 로그 출력
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Python Server] " + line);
                    }
                } catch (IOException e) { }
            }).start();

        } catch (IOException e) {
            System.err.println("[Python] 서버 실행 실패: " + e.getMessage());
        }
    }

    // 파이썬 실행 경로 찾기 (venv 우선)
    private String getPythonExecutable() {
        // Windows
        File venvWin = new File(PY_DIR + "/venv/Scripts/python.exe");
        if (venvWin.exists()) return venvWin.getAbsolutePath();

        // Mac/Linux
        File venvUnix = new File(PY_DIR + "/venv/bin/python");
        if (venvUnix.exists()) return venvUnix.getAbsolutePath();

        return "python"; // 시스템 파이썬
    }

    @PreDestroy
    public void stopPythonServer() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            System.out.println("[Python] 분석 서버 종료...");
            pythonProcess.destroy();
        }
    }
}