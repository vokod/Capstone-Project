package com.awolity.trakr.di;

import android.content.Context;

import com.awolity.trakr.TrakrApplication;
import com.awolity.trakrutils.AppExecutors;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@SuppressWarnings("WeakerAccess")
@Module
public class AppModule {

    private final TrakrApplication app;
    private final AppExecutors executors;

    public AppModule(TrakrApplication app) {
        this.app = app;
        executors = new AppExecutors();
    }

    @Provides
    @Singleton
    public Context provideContext() {
        return app;
    }

    @Provides
    @Singleton
    public Executor provideDiscIOExecutor() {
        return executors.diskIO();
    }

}