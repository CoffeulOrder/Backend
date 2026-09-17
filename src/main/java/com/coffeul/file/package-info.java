/**
 * 메뉴 이미지 업로드/교체. 비공개 S3 버킷 + CloudFront(OAC)로만 읽기, DB엔 URL이 아닌 키만 저장.
 * 담당: 태완 형. 교체 시 새 키 사용(캐시 무효화 비용 없음), 이전 파일 정리는 베타 이후.
 */
package com.coffeul.file;
