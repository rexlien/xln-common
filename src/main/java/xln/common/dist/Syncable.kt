package xln.common.dist

import com.google.protobuf.AbstractMessage
import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.GeneratedMessageV3
import mvccpb.Kv
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.max
import kotlin.math.min

interface Versioned  {

    fun getVersion(): Long
    fun getModRevision(): Long
    fun getCreateRevision(): Long

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
        history[versioned.getVersion()] = versioned

        headVersion = max(headVersion, versioned.getVersion())
        tailVersion = min(tailVersion, versioned.getVersion())


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


open class VersioneWrapper<T>(var value: T,
                              private var version: Long, private var modRevision: Long, private var createRevision: Long) : Versioned {



    constructor(value: T, kv: Kv.KeyValue) : this(value, kv.version, kv.modRevision, kv.createRevision)

    fun set(value: T, version: Long, modRevision: Long, createRevision: Long) {
        this.value = value
        this.version = version
        this.modRevision = modRevision
        this.createRevision = createRevision
    }



        override fun getVersion(): Long {
            return version
        }

        override fun getModRevision(): Long {
            return modRevision
        }

        override fun getCreateRevision(): Long {
            return createRevision
        }

    override var deleteRevision: Long = 0L


}


class VersionedProto<T: AbstractMessage> : VersioneWrapper<T> {

    constructor(kv: Kv.KeyValue, valueParser: (ByteString) -> T ) : super(valueParser(kv.value), kv.version, kv.modRevision, kv.createRevision){

    }


}

/**
 *
*/
class VersionedProp() : Versioned {

    override fun getVersion(): Long {
        return prop?.getVersion()?: 0L
    }

    override fun getModRevision(): Long {
        return prop?.getModRevision()?: 0L
    }

    override fun getCreateRevision(): Long {
        return prop?.getCreateRevision()?: 0L
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

        if(prop!!.getModRevision() > this.prop!!.getModRevision()) {
            if(this.prop!!.getCreateRevision() != prop.getCreateRevision()) {
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
            if (prop!!.getModRevision() > this.prop!!.getModRevision()) {
                if(this.prop!!.getCreateRevision() == prop.getCreateRevision()) {

                    this.prop!!.deleteRevision = prop.getModRevision()

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



