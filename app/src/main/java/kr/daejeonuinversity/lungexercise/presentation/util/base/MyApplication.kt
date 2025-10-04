package kr.daejeonuinversity.lungexercise.presentation.util.base

import android.app.Application
import kr.daejeonuinversity.lungexercise.presentation.util.util.databaseModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MyApplication)
            modules(
                listOf(
                    databaseModule,
                )
            )
        }
    }
}