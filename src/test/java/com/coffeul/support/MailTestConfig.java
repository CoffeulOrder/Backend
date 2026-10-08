package com.coffeul.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 인증 메일을 가짜 어댑터로 바꾼다. 같은 설정을 쓰는 테스트끼리는 Spring 컨텍스트를 공유하므로
 * 메일이 필요한 테스트는 전부 이걸 @Import 한다 (컨텍스트가 여러 벌 뜨면 그만큼 느려진다).
 */
@TestConfiguration
public class MailTestConfig {

    @Bean
    @Primary
    public RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
