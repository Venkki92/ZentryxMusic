package com.zentryx.music.di

import com.zentryx.music.network.metadata.RangeMetadataReader
import com.zentryx.music.network.webdav.WebDavClient
import com.zentryx.music.network.webdav.WebDavUrlHelper
import com.zentryx.music.network.webdav.WebDavXmlParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideWebDavUrlHelper(): WebDavUrlHelper = WebDavUrlHelper()

    @Provides
    @Singleton
    fun provideWebDavXmlParser(urlHelper: WebDavUrlHelper): WebDavXmlParser =
        WebDavXmlParser(urlHelper)

    @Provides
    @Singleton
    fun provideWebDavClient(
        baseOkHttpClient: OkHttpClient,
        xmlParser: WebDavXmlParser,
        urlHelper: WebDavUrlHelper,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): WebDavClient = WebDavClient(baseOkHttpClient, xmlParser, urlHelper, ioDispatcher)

    @Provides
    @Singleton
    fun provideRangeMetadataReader(
        webDavClient: WebDavClient
    ): RangeMetadataReader = RangeMetadataReader(webDavClient)
}
