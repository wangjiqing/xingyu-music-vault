package com.xingyu.musicvault.openapi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OpenApiSyncStateService {
    static final int SINGLETON_ID = 1;

    @Transactional
    public OpenApiLibraryState current() {
        return state();
    }

    OpenApiLibraryState state() {
        OpenApiLibraryState state = OpenApiLibraryState.findById(SINGLETON_ID);
        if (state != null) {
            return state;
        }
        state = new OpenApiLibraryState();
        state.id = SINGLETON_ID;
        state.libraryVersion = 1;
        state.persist();
        return state;
    }
}
