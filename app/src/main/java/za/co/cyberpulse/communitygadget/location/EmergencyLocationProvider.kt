package za.co.cyberpulse.communitygadget.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class EmergencyLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    @SuppressLint("MissingPermission")
    suspend fun currentEmergencyLocation(): Location? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val tokenSource = CancellationTokenSource()
        return withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { tokenSource.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        } ?: lastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): Location? = suspendCancellableCoroutine { continuation ->
        client.lastLocation
            .addOnSuccessListener { location -> if (continuation.isActive) continuation.resume(location) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
            .addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
    }
}
