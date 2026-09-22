package com.coffeul.verification.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * SES 클라이언트. {@code MAIL_PROVIDER=ses}일 때만 만든다 — 조건 없이 두면 로컬 · 테스트에서도
 * AWS 자격을 찾으려 들어서 뜨지 않는 빈 하나 때문에 앱 전체가 안 뜬다.
 *
 * <p>자격 증명은 기본 제공자 체인에 맡긴다(EC2는 IAM 역할, 로컬은 환경변수 · ~/.aws). 액세스 키를
 * 설정 파일에 두지 않는다 — 키가 Git이나 이미지에 박히는 순간 회수 비용이 훨씬 커진다.
 */
@Configuration
@ConditionalOnProperty(name = "coffeul.verification.mail-provider", havingValue = "ses")
public class SesClientConfig {

    @Bean
    SesV2Client sesV2Client(@Value("${coffeul.aws.region:ap-northeast-2}") String region) {
        return SesV2Client.builder().region(Region.of(region)).build();
    }
}
