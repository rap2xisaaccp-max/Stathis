package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {

    @Provides
    @Singleton
    fun provideAdaptiveQueueDatabase(@ApplicationContext context: Context): AdaptiveQueueDatabase =
        AdaptiveQueueDatabase.open(context)

    @Provides
    @Singleton
    fun providePersistentAdaptiveQueue(
        @ApplicationContext context: Context,
        db: AdaptiveQueueDatabase,
        gson: Gson
    ): PersistentAdaptiveQueue = PersistentAdaptiveQueue(context, db, gson)
}
