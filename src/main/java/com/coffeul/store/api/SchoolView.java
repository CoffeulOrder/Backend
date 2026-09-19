package com.coffeul.store.api;

public record SchoolView(Long id, String name, String campus, String status) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
