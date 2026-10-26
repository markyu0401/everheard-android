package io.github.markyu0401.everheard.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnlineClusterTest {

    @Test
    fun separates_orthogonal_embeddings() {
        val cluster = OnlineCluster()
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f, 0f)

        val first = cluster.assign(a)
        val second = cluster.assign(b)
        val again = cluster.assign(a)

        assertThat(first).isNotEqualTo(second)
        assertThat(again).isEqualTo(first)
        assertThat(cluster.clusterCount()).isEqualTo(2)
    }

    @Test
    fun similar_embeddings_join_the_same_cluster() {
        val cluster = OnlineCluster(threshold = 0.70f)
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val similar = floatArrayOf(0.95f, 0.05f, 0f, 0f)

        assertThat(cluster.assign(a)).isEqualTo(cluster.assign(similar))
        assertThat(cluster.clusterCount()).isEqualTo(1)
    }

    @Test
    fun rename_changes_the_label() {
        val cluster = OnlineCluster()
        val id = cluster.assign(floatArrayOf(1f, 0f))

        cluster.rename(id, "Me")

        assertThat(cluster.label(id)).isEqualTo("Me")
    }

    @Test
    fun merge_combines_two_clusters() {
        val cluster = OnlineCluster()
        val first = cluster.assign(floatArrayOf(1f, 0f, 0f))
        val second = cluster.assign(floatArrayOf(0f, 1f, 0f))

        cluster.merge(first, second)

        assertThat(cluster.clusterCount()).isEqualTo(1)
        assertThat(cluster.label(first)).isNotEmpty()
    }

    @Test
    fun zero_vector_is_rejected() {
        val cluster = OnlineCluster()

        assertThat(cluster.assign(floatArrayOf(0f, 0f, 0f))).isEqualTo(-1)
        assertThat(cluster.clusterCount()).isEqualTo(0)
    }
}
