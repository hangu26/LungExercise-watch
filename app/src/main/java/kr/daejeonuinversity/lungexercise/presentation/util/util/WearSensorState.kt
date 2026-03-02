package kr.daejeonuinversity.lungexercise.presentation.util.util

object WearSensorState {
    @Volatile var heartRate: Int = 0
    @Volatile var stepCount: Int = 0
}