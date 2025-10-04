package kr.daejeonuinversity.lungexercise.presentation.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import kr.daejeonuinversity.lungexercise.presentation.data.local.dao.StepDao
import kr.daejeonuinversity.lungexercise.presentation.data.local.entity.StepDataEntity

@Database(
    entities = [StepDataEntity::class],
    version = 2
)
abstract class StepDatabase : RoomDatabase() {

    abstract fun stepDao(): StepDao

}