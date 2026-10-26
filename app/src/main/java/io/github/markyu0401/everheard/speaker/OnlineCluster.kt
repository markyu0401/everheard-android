package io.github.markyu0401.everheard.speaker

import kotlin.math.sqrt

/**
 * Online speaker clustering with cosine similarity.
 *
 * A new embedding joins the closest cluster when similarity is at least
 * [threshold]; otherwise it creates a cluster. Centroids are running means of
 * L2-normalised embeddings, re-normalised after every update.
 */
class OnlineCluster(
    private val threshold: Float = 0.70f
) {

    private val centroids = mutableListOf<FloatArray>()
    private val counts = mutableListOf<Int>()
    private val labels = mutableMapOf<Int, String>()

    @Synchronized
    fun assign(embedding: FloatArray): Int {
        val normalized = normalize(embedding) ?: return -1

        var bestIndex = -1
        var bestSimilarity = -1.0f
        for (index in centroids.indices) {
            val similarity = cosine(normalized, centroids[index])
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = index
            }
        }

        if (bestIndex >= 0 && bestSimilarity >= threshold) {
            updateCentroid(bestIndex, normalized)
            return bestIndex
        }

        centroids += normalized.copyOf()
        counts += 1
        return centroids.lastIndex
    }

    @Synchronized
    fun clusterCount(): Int = centroids.size

    @Synchronized
    fun label(clusterId: Int): String =
        labels[clusterId] ?: defaultLabel(clusterId)

    @Synchronized
    fun rename(clusterId: Int, name: String) {
        if (clusterId in centroids.indices) {
            labels[clusterId] = name
        }
    }

    @Synchronized
    fun merge(first: Int, second: Int) {
        if (first == second || first !in centroids.indices || second !in centroids.indices) {
            return
        }
        val firstCount = counts[first]
        val secondCount = counts[second]
        val merged = FloatArray(centroids[first].size) { index ->
            (centroids[first][index] * firstCount + centroids[second][index] * secondCount) /
                (firstCount + secondCount)
        }
        centroids[first] = normalize(merged) ?: centroids[first]
        counts[first] = firstCount + secondCount

        centroids.removeAt(second)
        counts.removeAt(second)
        labels.remove(second)
        labels[first] = labels[first] ?: defaultLabel(first)
    }

    @Synchronized
    fun reset() {
        centroids.clear()
        counts.clear()
        labels.clear()
    }

    private fun updateCentroid(index: Int, normalized: FloatArray) {
        val count = counts[index]
        val current = centroids[index]
        val updated = FloatArray(current.size) { offset ->
            (current[offset] * count + normalized[offset]) / (count + 1)
        }
        centroids[index] = normalize(updated) ?: current
        counts[index] = count + 1
    }

    private fun defaultLabel(index: Int): String = "Speaker ${('A'.code + index).toChar()}"

    private fun normalize(vector: FloatArray): FloatArray? {
        var sumSquares = 0.0
        for (value in vector) {
            sumSquares += value.toDouble() * value
        }
        if (sumSquares <= 1e-12) return null
        val norm = sqrt(sumSquares).toFloat()
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return -1.0f
        var dot = 0.0f
        for (index in left.indices) {
            dot += left[index] * right[index]
        }
        return dot
    }
}
