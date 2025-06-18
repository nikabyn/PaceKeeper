    package org.htwk.pacing.backend.data_collection

    import android.content.Context
    import android.util.Log
    import androidx.health.connect.client.HealthConnectClient
    import androidx.health.connect.client.permission.HealthPermission
    import androidx.health.connect.client.records.HeartRateRecord
    import androidx.health.connect.client.request.ReadRecordsRequest
    import androidx.health.connect.client.time.TimeRangeFilter
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import java.time.Instant

    object HealthConnectHelper {
        private const val TAG = "HealthConnectHelper"
        private val permissions = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )

        fun readHeartRateData(context: Context) {
            val client = HealthConnectClient.getOrCreate(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val endTime = Instant.now()
                    val startTime = endTime.minusSeconds(60 * 60 * 24) // letzte 24h

                    val response = client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                        )
                    )
                    for (record in response.records) {
                        Log.d(TAG, "HeartRate: ${record.samples.map { sample -> sample.beatsPerMinute }} @ ${record.startTime}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading heart rate data", e)
                }
            }
        }

        fun getRequiredPermissions() = permissions
    }