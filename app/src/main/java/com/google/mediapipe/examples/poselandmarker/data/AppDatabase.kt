package com.google.mediapipe.examples.poselandmarker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [SampleEntity::class, RatingEntity::class],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun sampleDao(): SampleDao
  abstract fun ratingDao(): RatingDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null
    fun getInstance(ctx: Context): AppDatabase =
      INSTANCE ?: synchronized(this) {
        Room.databaseBuilder(
          ctx.applicationContext,
          AppDatabase::class.java, "workout-db"
        ).build().also { INSTANCE = it }
      }
  }
}
