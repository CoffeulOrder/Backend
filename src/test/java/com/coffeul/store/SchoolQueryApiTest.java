package com.coffeul.store;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.store.api.SchoolQueryApi;
import com.coffeul.store.api.SchoolView;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** store.api.SchoolQueryApi 검증 — SM-3 · SM-4 응답이 필요로 하는 school.name · campus 조회. */
class SchoolQueryApiTest extends AbstractIntegrationTest {

    @Autowired
    private SchoolQueryApi schoolQueryApi;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 존재하는_학교_id로_조회하면_이름과_캠퍼스를_돌려준다() {
        TestFixtures fixtures = new TestFixtures(jdbcTemplate);
        long schoolId = fixtures.createSchool("을지대학교-" + UUID.randomUUID().toString().substring(0, 8));

        Optional<SchoolView> found = schoolQueryApi.findById(schoolId);

        assertThat(found).isPresent();
        SchoolView school = found.get();
        assertThat(school.id()).isEqualTo(schoolId);
        assertThat(school.campus()).isEqualTo("테스트캠퍼스");
        assertThat(school.isActive()).isTrue();
    }

    @Test
    void 존재하지_않는_학교_id는_빈_값을_돌려준다() {
        assertThat(schoolQueryApi.findById(-1L)).isEmpty();
    }
}
