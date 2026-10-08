package com.coffeul.store.presentation;

public record StoreStatusChangeRequest(String status, Boolean force) {

    public boolean forceOrDefault() {
        return Boolean.TRUE.equals(force);
    }
}
