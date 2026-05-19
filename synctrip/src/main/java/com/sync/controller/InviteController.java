package com.sync.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InviteController {

    @GetMapping(value = "/invite", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> inviteLanding(@RequestParam String code) {
        if (!code.matches("[A-Za-z0-9]{1,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 초대 코드입니다.");
        }
        String deepLink = "synctrip://band/join?code=" + code;
        String html = """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>SyncTrip 초대</title>
                    <style>
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                               background: #F0F4FF; display: flex; align-items: center;
                               justify-content: center; min-height: 100vh; padding: 20px; }
                        .card { background: white; border-radius: 20px; padding: 48px 32px;
                                max-width: 380px; width: 100%%; text-align: center;
                                box-shadow: 0 8px 32px rgba(74, 124, 255, 0.15); }
                        h2 { color: #1A1A2E; font-size: 22px; margin-bottom: 12px; }
                        p { color: #6B7280; font-size: 15px; line-height: 1.6; margin-bottom: 32px; }
                        .btn { display: block; padding: 16px; background: #4A7CFF;
                               color: white; border-radius: 12px; text-decoration: none;
                               font-size: 16px; font-weight: 700; }
                        .btn:active { opacity: 0.8; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>SyncTrip에 초대되었습니다!</h2>
                        <p>함께 여행 일정을 계획해보세요.<br>아래 버튼을 눌러 앱에서 확인하세요.</p>
                        <a class="btn" href="%s">앱으로 열기</a>
                    </div>
                    <script>
                        setTimeout(function() {
                            window.location.href = '%s';
                        }, 800);
                    </script>
                </body>
                </html>
                """.formatted(deepLink, deepLink);
        return ResponseEntity.ok(html);
    }
}
