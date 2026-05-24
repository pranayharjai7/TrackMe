package com.trackme.phone.wear

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearSyncEntryPoint {
    fun watchConnectionManager(): WatchConnectionManager
    fun watchSyncRepository(): WatchSyncRepository
}

object WearSyncInitializer {
    fun start(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WearSyncEntryPoint::class.java)
        entryPoint.watchConnectionManager().start()
        entryPoint.watchSyncRepository().start()
    }
}
