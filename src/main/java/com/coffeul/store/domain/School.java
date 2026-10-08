package com.coffeul.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `school` 테이블 매핑. */
@Entity
@Table(name = "school")
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "campus", length = 50)
    private String campus;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected School() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCampus() {
        return campus;
    }

    public String getStatus() {
        return status;
    }
}
