package com.app.room_retrofit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.app.room_retrofit.data.local.dao.ArticleDao
import com.app.room_retrofit.data.local.entity.ArticleEntity

@Database(entities = [ArticleEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
}
