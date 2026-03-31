package com.example.oto1720.dojo2026.di

import com.example.oto1720.dojo2026.data.network.ConnectivityNetworkMonitor
import com.example.oto1720.dojo2026.data.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkMonitorModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
