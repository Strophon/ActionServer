package com.github.strophon.data.transfer;


import com.github.strophon.data.DataIO;

public class DataSource {
    private ObjectContainer container;
    private DataIO dio;

    public DataSource(ObjectContainer container) {
        this.container = container;
    }

    public DataSource(DataIO dio) {
        this.dio = dio;
    }

    public boolean useDatabase() {
        return container == null && dio != null;
    }

    public ObjectContainer getContainer() {
        return container;
    }

    public DataIO getDataIO() {
        return dio;
    }
}

