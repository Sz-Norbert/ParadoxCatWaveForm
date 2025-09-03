package com.paradoxcat.waveformtest.di

import com.paradoxcat.waveformtest.repositories.AudioRepository
import com.paradoxcat.waveformtest.repositories.AudioRepositoryImpl
import com.paradoxcat.waveformtest.services.AudioPlayerService
import com.paradoxcat.waveformtest.services.AudioPlayerServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAudioRepository(
        audioRepositoryImpl: AudioRepositoryImpl
    ): AudioRepository


    @Binds
    abstract fun bindAudioPlayerService(
        audioPlayerServiceImpl: AudioPlayerServiceImpl
    ): AudioPlayerService



}