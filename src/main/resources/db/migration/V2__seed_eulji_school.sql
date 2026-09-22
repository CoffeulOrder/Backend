-- 1차 스코프 학교: 을지대학교 성남캠퍼스 (설계안 ver 0.7 확정 사항)
--
-- 이건 erd.py가 만드는 스키마가 아니라 기준 데이터다. 스키마를 바꾸지 않으므로 erd.py는 건드리지 않는다.
-- 학교를 Flyway로 넣는 이유: school_email_domain에 행이 없으면 REQ-EV-001이 모든 가입을 EV001로 막아서
-- 서버가 아예 회원을 못 받는다. merchant·store는 사장님 서류(사업자등록번호 등)가 나와야 해서 여기 없다.
--
-- 새 학교를 받을 땐 코드가 아니라 이 방식으로 행을 추가한다 (설계안: "학교는 코드가 아니라 데이터").

INSERT INTO `school` (`name`, `campus`, `status`)
VALUES ('을지대학교', '성남캠퍼스', 'ACTIVE');

INSERT INTO `school_email_domain` (`school_id`, `domain`)
SELECT `id`, 'g.eulji.ac.kr'
FROM `school`
WHERE `name` = '을지대학교' AND `campus` = '성남캠퍼스';
