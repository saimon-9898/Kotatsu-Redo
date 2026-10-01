package org.koitharu.kotatsu.pornhwadb

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Dagger module for Pornhwa DB source dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object PornhwaDbModule {

    @Provides
    @Singleton
    fun providePornhwaDbConfig(): PornhwaDbConfig {
        return PornhwaDbConfig()
    }

    @Provides
    @Singleton
    fun providePornhwaDbApiClient(
        config: PornhwaDbConfig,
        httpClient: OkHttpClient,
    ): PornhwaDbApiClient {
        return PornhwaDbApiClient(config, httpClient)
    }
}
