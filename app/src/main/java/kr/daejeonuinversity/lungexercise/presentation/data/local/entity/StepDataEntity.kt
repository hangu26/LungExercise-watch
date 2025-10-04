package kr.daejeonuinversity.lungexercise.presentation.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "step_data")
data class StepDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stepCount: Int,          // 걸음 수
    val startTime: Long,         // interval 시작 시각 (epoch millis)
    val endTime: Long,           // interval 종료 시각 (epoch millis)
    val createdAt: Long = System.currentTimeMillis() // DB 저장 시간
)