package xln.common.dist

import com.google.protobuf.AbstractMessage
import org.apache.groovy.util.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.max
import kotlin.math.min

interface Versioned  {

    fun version(): Long
    fun modRevision(): Long
    fun createRevision(): Long

    var deleteRevision : Long

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

interface Actor {

    suspend fun send(message : AbstractMessage)
}

class VersionHistory {

     @Synchronized fun checkPoint() {
        if(history.isEmpty()) {
            return
        }

        for(i in tailVersion until  headVersion ) {
            history.remove(i)
        }
         tailVersion = headVersion
    }

    @Synchronized fun clear() {
        history.clear()
        headVersion = Long.MIN_VALUE
        tailVersion = Long.MAX_VALUE
    }

    @Synchronized fun add(versioned: Versioned) {
        history[versioned.version()] = versioned

        headVersion = max(headVersion, versioned.version())
        tailVersion = min(tailVersion, versioned.version())


        //history.floorKey()

    }

    fun getHead() : Versioned? {
        if(headVersion == Long.MIN_VALUE) {
            return null
        }
        return history[headVersion]
    }

    val history = ConcurrentSkipListMap<Long, Versioned>()
    var headVersion = Long.MIN_VALUE
    var tailVersion = Long.MAX_VALUE

}

/**
 *
*/
class VersionedProp() : Versioned {

    override fun version(): Long {
        return prop?.version()?: 0L
    }

    override fun modRevision(): Long {
        return prop?.modRevision()?: 0L
    }

    override fun createRevision(): Long {
        return prop?.createRevision()?: 0L
    }

    override var deleteRevision = 0L
        get() {return prop?.deleteRevision?:0L}


    constructor(prop: Versioned) : this(){
        this.prop = prop
        versionHistory.add(this.prop!!)
    }

    private var prop : Versioned? = null

    fun setProp(prop: Versioned) : Boolean {

        if(this.prop == null) {
            this.prop = prop
            versionHistory.add(this.prop!!)
            return true
        }

        if(prop!!.modRevision() > this.prop!!.modRevision()) {
            if(this.prop!!.createRevision() != prop.createRevision()) {
                versionHistory.clear()
            }
            this.prop = prop
            versionHistory.add(this.prop!!)
            versionHistory.checkPoint()
            return true
        }
        return false
    }

    fun deleteProp(prop: Versioned) {
        if(this.prop != null) {
            if (prop!!.modRevision() > this.prop!!.modRevision()) {
                if(this.prop!!.createRevision() == prop.createRevision()) {

                    this.prop!!.deleteRevision = prop.modRevision()

                }
            }
        }
    }


    fun getProp() : Versioned? {
        return versionHistory.getHead()
    }

    private val versionHistory = VersionHistory()


    inline fun <reified T> asT() : T? {

        val prop = getProp()
        if(prop != null) {
            when(prop) {
                is T -> {
                    return prop
                }
            }
        }
        return null
    }

}



