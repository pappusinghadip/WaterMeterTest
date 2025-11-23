package com.example.watermetertest.di

import android.content.Context
import com.example.watermetertest.data.repository.AuthRepository
import com.example.watermetertest.data.repository.BluetoothRepository
import com.example.watermetertest.data.repository.PermissionsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBluetoothRepository(@ApplicationContext context: Context): BluetoothRepository {
        return BluetoothRepository(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(@ApplicationContext context: Context): AuthRepository {
        return AuthRepository(context)
    }

    @Provides
    @Singleton
    fun providePermissionsRepository(@ApplicationContext context: Context): PermissionsRepository {
        return PermissionsRepository(context)
    }
}
