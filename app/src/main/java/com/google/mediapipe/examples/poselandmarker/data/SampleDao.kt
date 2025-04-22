package com.google.mediapipe.examples.poselandmarker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SampleDao {
  // returns void (Unit) → Room will treat as a normal insert
  @Insert
  fun insert(sample: SampleEntity)

  // returns List<SampleEntity> directly → Room knows how to map
  @Query("SELECT * FROM samples ORDER BY timestamp DESC LIMIT :limit")
  fun getRecent(limit: Int): List<SampleEntity>
}
