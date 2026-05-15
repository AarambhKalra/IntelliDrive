package aarambh.apps.intellidrive.util

object SafetyScoreCalculator {
    const val INITIAL_SCORE = 100
    private const val HARSH_BRAKING_PENALTY = 5
    private const val OVERSPEED_PENALTY = 3
    private const val ROUTE_DEVIATION_PENALTY = 10

    fun calculateScore(
        harshBrakingCount: Int,
        overspeedCount: Int,
        routeDeviationCount: Int
    ): Int {
        val totalPenalty = (harshBrakingCount * HARSH_BRAKING_PENALTY) +
                (overspeedCount * OVERSPEED_PENALTY) +
                (routeDeviationCount * ROUTE_DEVIATION_PENALTY)

        val score = INITIAL_SCORE - totalPenalty
        return score.coerceAtLeast(0) // Minimum score is 0
    }
}
