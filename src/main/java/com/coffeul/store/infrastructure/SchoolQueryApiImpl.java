package com.coffeul.store.infrastructure;

import com.coffeul.store.api.SchoolQueryApi;
import com.coffeul.store.api.SchoolView;
import com.coffeul.store.domain.School;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SchoolQueryApiImpl implements SchoolQueryApi {

    private final SchoolRepository schoolRepository;

    public SchoolQueryApiImpl(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Override
    public Optional<SchoolView> findById(Long schoolId) {
        return schoolRepository.findById(schoolId)
                .map(s -> new SchoolView(s.getId(), s.getName(), s.getCampus(), s.getStatus()));
    }
}
