package com.google.mediapipe.examples.poselandmarker.rl

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

private val Context.dataStore by preferencesDataStore("rl_prefs")
data class State(val avgAngleBucket: Int, val errorCount: Int)
typealias QTable = MutableMap<State, MutableMap<Int, Float>>

class QLearningAgent(private val ctx: Context) {
  companion object {
    private val Q_KEY = stringPreferencesKey("q_table")
    private val gson = Gson()
  }
  private var qTable: QTable = mutableMapOf()
  private val actions = listOf(-1, 0, +1)

  init { 
    try {
      load() 
    } catch (e: Exception) {
      // If loading fails, start with a fresh QTable
      qTable = mutableMapOf()
      // Save the fresh state to avoid future loading issues
      save()
    }
  }
  
  private fun load() = runBlocking {
    try {
      ctx.dataStore.data.first()[Q_KEY]?.let {
        val type = object: TypeToken<QTable>(){}.type
        qTable = gson.fromJson(it, type)
      }
    } catch (e: JsonParseException) {
      // If parsing fails, reset the QTable
      qTable = mutableMapOf()
      // And clear the stored data
      ctx.dataStore.edit { prefs ->
        prefs.remove(Q_KEY)
      }
    } catch (e: Exception) {
      // Handle any other exceptions
      qTable = mutableMapOf()
    }
  }
  
  private fun save() = runBlocking {
    try {
      ctx.dataStore.edit { it[Q_KEY] = gson.toJson(qTable) }
    } catch (e: Exception) {
      // If saving fails, just log and continue
      android.util.Log.e("QLearningAgent", "Failed to save QTable: ${e.message}")
    }
  }

  fun selectAction(state: State): Int {
    val row = qTable.getOrPut(state) { actions.associateWith{0f}.toMutableMap() }
    return if (Random.nextFloat()<0.1f) actions.random()
           else row.maxByOrNull{ it.value }!!.key
  }

  fun update(state: State, action: Int, reward: Float, next: State) {
    val α=0.1f; val γ=0.9f
    val row = qTable.getOrPut(state){ actions.associateWith{0f}.toMutableMap() }
    val qsa = row[action]!!
    val maxNext = qTable[next]?.values?.maxOrNull() ?: 0f
    row[action] = qsa + α*(reward + γ*maxNext - qsa)
    save()
  }
}