package dev.malik.phonebot.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class RunTrigger { SCHEDULE, MANUAL }

enum class RunOutcome { RUNNING, CHECKED, NO_NEW_DRAFT, SUCCESS, DRY_RUN, FAILED, INTERRUPTED }

/**
 * One row per inbox check. Posted drafts stay listed in TikTok's inbox, so a SUCCESS row's
 * [itemAt] is what stops the next check from posting the same draft again.
 */
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flowId: String,
    val trigger: RunTrigger,
    val startedAt: Long,
    val outcome: RunOutcome,
    val reason: String? = null,
    val durationMs: Long? = null,
    val screenshotPath: String? = null,
    /** Estimated time the posted inbox item arrived, from TikTok's "5m ago"-style label. */
    val itemAt: Long? = null,
)

@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(entity = RunEntity::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("runId")],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val at: Long,
    val name: String,
    val ok: Boolean,
    val detail: String? = null,
)

@Dao
interface RunDao {
    @Insert
    suspend fun insertRun(run: RunEntity): Long

    @Update
    suspend fun updateRun(run: RunEntity)

    @Insert
    suspend fun insertStep(step: StepEntity): Long

    /** The newest draft already posted; anything at or before it is never posted again. */
    @Query("SELECT MAX(itemAt) FROM runs WHERE flowId = :flowId AND outcome = 'SUCCESS'")
    suspend fun lastPostedItemAt(flowId: String): Long?

    @Query("SELECT * FROM runs ORDER BY startedAt DESC LIMIT :limit")
    fun recentRuns(limit: Int = 20): Flow<List<RunEntity>>

    @Query("SELECT * FROM steps WHERE runId = :runId ORDER BY at")
    suspend fun stepsFor(runId: Long): List<StepEntity>
}

@Database(entities = [RunEntity::class, StepEntity::class], version = 1)
abstract class BotDatabase : RoomDatabase() {
    abstract fun runs(): RunDao

    companion object {
        fun create(context: Context): BotDatabase =
            Room.databaseBuilder(context, BotDatabase::class.java, "phonebot.db").build()
    }
}
