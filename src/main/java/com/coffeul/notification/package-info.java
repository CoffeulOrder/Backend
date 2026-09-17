/**
 * 푸시 토큰 등록, Expo 발송(새 주문/픽업 준비 알림), 영수증 확인 후 죽은 토큰 비활성.
 * 담당: 태완 형. 규칙: 관리자앱 토큰은 매장 필수. 알림이 실패해도 주문은 성공으로 남는다(order → notification은 이벤트만).
 */
package com.coffeul.notification;
