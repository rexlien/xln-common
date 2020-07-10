package xln.common.dist

interface Versioned  {

    fun version(): Long
    fun modRevision(): Long
    fun createRevision(): Long

}

interface ClusterAware  {

    fun subscribeCluster(cluster: Cluster) {
        cluster.getClusterEventSource().subscribe {
            onClusterEvent(it)
        }
    }

    fun onClusterEvent(clusterEvent: ClusterEvent)
    suspend fun onLeaderChanged(clusterEvent: ClusterEvent, isLeader : Boolean )

}



