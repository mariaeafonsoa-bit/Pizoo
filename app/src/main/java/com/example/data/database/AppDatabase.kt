package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Chat
import com.example.data.model.GroupMember
import com.example.data.model.Message
import com.example.data.model.User

@Database(
    entities = [User::class, Chat::class, Message::class, GroupMember::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secure_chat_database"
                )
                .fallbackToDestructiveMigration() // safe during development cycles
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
