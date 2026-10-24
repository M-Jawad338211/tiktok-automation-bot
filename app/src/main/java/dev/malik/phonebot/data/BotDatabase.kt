package dev.malik.phonebot.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class RunOutcome { IGNORED, MATCHED, RUNNING, SUCCESS, DRY_RUN, FAILED, INTERRUPTED }

/** One row per trigger. (notifKey, postTime) is unique so a notification is never run twice. */
@Entity(
    tableName = "runs",
    indices = [Index(value = ["notifKey", "postTime"], unique = true)],
)
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flowId: String,
    val notifKey: String,
    val postTime: Long,
    val triggeredAt: Long,
    val outcome: RunOutcome,
    val reason: String? = null,
    val durationMs: Long? = null,
    val screenshotPath: String? = null,
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
    /** Returns -1 when this notification was already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRun(run: RunEntity): Long

    @Insert
    suspend fun insertStep(step: StepEntity): Long

    @Query("SELECT * FROM runs ORDER BY triggeredAt DESC LIMIT :limit")
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
