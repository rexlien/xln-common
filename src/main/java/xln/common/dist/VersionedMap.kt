package xln.common.dist

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

fun ConcurrentHashMap<String, Versioned>.versionAdd(key : String, versioned : Versioned ) {


    this.compute(key) { k, v ->
        if(v == null) {
            versioned
        }
        else if(v.modRevision() > versioned.modRevision()) {
            //log.debug("update ${key} rejected ${versioned.modRevision()}")
            v
        } else {
            versioned
        }
    }

}

fun ConcurrentHashMap<String, Versioned>.versionRemove(key : String, versioned : Versioned ) {

    this.computeIfPresent(key) { k, v ->
        if(v.modRevision() > versioned.modRevision()) {
            v
        } else {
            null
        }
    }

}