package dev.eweather.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.eweather.data.db.dao.AlertDao
import dev.eweather.data.db.dao.LocationDao
import dev.eweather.data.db.dao.WeatherDao
import dev.eweather.data.db.entity.AlertEntity
import dev.eweather.data.db.entity.LocationEntity
import dev.eweather.data.db.entity.WeatherCacheEntity

@Database(
    entities = [
        WeatherCacheEntity::class,
        LocationEntity::class,
        AlertEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun locationDao(): LocationDao
    abstract fun alertDao(): AlertDao
}
