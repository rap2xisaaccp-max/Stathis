package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveFeedbackDelivery
import citu.edu.stathis.mobile.features.exercise.adaptive.CoachingDelivery
import citu.edu.stathis.mobile.features.exercise.adaptive.EvidenceQueue
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceCapture
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceCaptureImpl
import citu.edu.stathis.mobile.features.exercise.adaptive.OfflineQueue
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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

    @Provides
    @Singleton
    fun provideOfflineQueue(persistent: PersistentAdaptiveQueue): OfflineQueue = persistent

    @Provides
    @Singleton
    fun provideEvidenceQueue(
        @ApplicationContext context: Context,
        db: AdaptiveQueueDatabase,
        gson: Gson
    ): EvidenceQueue = PersistentEvidenceQueue(context, db, gson)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EvidenceCaptureModule {
    @Binds
    @Singleton
    abstract fun bindFormEvidenceCapture(impl: FormEvidenceCaptureImpl): FormEvidenceCapture

    @Binds
    @Singleton
    abstract fun bindCoachingDelivery(impl: AdaptiveFeedbackDelivery): CoachingDelivery
}
