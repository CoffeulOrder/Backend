package com.coffeul.order.application;

import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** 고객 표시용 주문번호 (예: CF-20261016-7Q3K9X2M). 영업일 계산이 아닌 표시용이라 KST 날짜만 쓴다. */
final class OrderCodeGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // 헷갈리는 0/O, 1/I 제외
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderCodeGenerator() {
    }

    static String generate() {
        String date = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DATE_FORMAT);
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return "CF-" + date + "-" + suffix;
    }
}
