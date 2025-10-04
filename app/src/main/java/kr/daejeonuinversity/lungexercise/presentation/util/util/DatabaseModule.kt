package kr.daejeonuinversity.lungexercise.presentation.util.util

import androidx.room.Room
import kr.daejeonuinversity.lungexercise.presentation.data.local.StepDatabase
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            get(),
            StepDatabase::class.java,
            "step_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    single { get<StepDatabase>().stepDao() }

}
