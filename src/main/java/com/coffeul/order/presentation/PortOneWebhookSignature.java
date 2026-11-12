package com.coffeul.order.presentation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

final class PortOneWebhookSignature {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SECRET_PREFIX = "whsec_";
    private static final long TOLERANCE_SECONDS = Duration.ofMinutes(5).toSeconds();

    private final SecretKeySpec key;
    private final Clock clock;

    PortOneWebhookSignature(String secret, Clock clock) {
        this.clock = clock;
        try {
            String base64 = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
            key = new SecretKeySpec(Base64.getDecoder().decode(base64), ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("coffeul.payment.portone.webhook-secret must contain a valid signing key");
        }
    }

    boolean isValid(String id, String timestamp, String signature, byte[] body) {
        if (id == null || id.isBlank() || id.contains(".") || timestamp == null || signature == null
                || !isCurrent(timestamp)) {
            return false;
        }
        byte[] expected = sign(id, timestamp, body);
        for (String candidate : signature.strip().split("\\s+")) {
            if (candidate.startsWith("v1,") && matches(expected, candidate.substring(3))) {
                return true;
            }
        }
        return false;
    }

    private boolean isCurrent(String timestamp) {
        if (!timestamp.matches("[0-9]{1,19}")) {
            return false;
        }
        try {
            long sentAt = Long.parseLong(timestamp);
            long now = clock.instant().getEpochSecond();
            return sentAt >= now - TOLERANCE_SECONDS && sentAt <= now + TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private byte[] sign(String id, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Webhook signature verification is unavailable", e);
        }
    }

    private boolean matches(byte[] expected, String encoded) {
        try {
            return MessageDigest.isEqual(expected, Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
