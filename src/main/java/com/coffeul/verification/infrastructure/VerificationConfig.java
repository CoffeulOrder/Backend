package com.coffeul.verification.infrastructure;

import com.coffeul.verification.application.VerificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** verification 모듈 설정. 정책 값(coffeul.verification.*) 바인딩. */
@Configuration
@EnableConfigurationProperties(VerificationProperties.class)
public class VerificationConfig {
}
