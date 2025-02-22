import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buca_segnalazioni")
data class BucaSegnalazione(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accelerationMagnitude: Float,
    val rotationMagnitude: Float
)