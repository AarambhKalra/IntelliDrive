package aarambh.apps.intellidrive.util

import com.google.android.gms.maps.model.LatLng

/**
 * Decodes a Google Maps encoded polyline string into a list of [LatLng] points.
 *
 * Algorithm reference:
 * https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
fun decodePolyline(encoded: String): List<LatLng> {
    val result = mutableListOf<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        // ── Decode next latitude delta ─────────────────────────────────────────
        var shift = 0
        var accumulator = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            accumulator = accumulator or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (accumulator and 1 != 0) (accumulator shr 1).inv() else accumulator shr 1

        // ── Decode next longitude delta ────────────────────────────────────────
        shift = 0
        accumulator = 0
        do {
            b = encoded[index++].code - 63
            accumulator = accumulator or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (accumulator and 1 != 0) (accumulator shr 1).inv() else accumulator shr 1

        result.add(LatLng(lat / 1E5, lng / 1E5))
    }

    return result
}
