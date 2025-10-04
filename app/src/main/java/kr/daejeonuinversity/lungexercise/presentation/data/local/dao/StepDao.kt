package kr.daejeonuinversity.lungexercise.presentation.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kr.daejeonuinversity.lungexercise.presentation.data.local.entity.StepDataEntity

@Dao
interface StepDao {
    @Insert
    suspend fun insertStepData(data: StepDataEntity)

    @Query("SELECT * FROM step_data ORDER BY createdAt ASC")
    suspend fun getAllStepData(): List<StepDataEntity>

    @Query("DELETE FROM step_data")
    suspend fun deleteAll()

    @Query("SELECT * FROM step_data WHERE startTime = :startTime LIMIT 1")
    suspend fun getStepByInterval(startTime: Long): StepDataEntity?

    @Query("UPDATE step_data SET stepCount = :steps WHERE id = :id")
    suspend fun updateSteps(id: Int, steps: Int)

    @Query("DELETE FROM step_data WHERE id = :id")
    suspend fun deleteStepById(id: Int)

    @Transaction
    suspend fun upsertStep(stepData: StepDataEntity) {
        val existing = getStepByInterval(stepData.startTime)
        if (existing != null) {
            updateSteps(existing.id, existing.stepCount + stepData.stepCount)
        } else {
            insertStepData(stepData)
        }
    }
}