import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BucaDao {
    @Insert
    suspend fun insert(bucaSegnalazione: BucaSegnalazione)

    @Query("SELECT * FROM buca_segnalazioni ORDER BY timestamp DESC")
    suspend fun getAllBuche(): List<BucaSegnalazione>
}