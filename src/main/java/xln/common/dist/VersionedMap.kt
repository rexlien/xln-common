package xln.common.dist

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.Consumer

fun ConcurrentHashMap<String, Versioned>.versionAdd(key : String, versioned : Versioned, onAdd: Consumer<Versioned>?) {


    this.compute(key) { k, v ->
        if(v == null) {
            onAdd?.accept(versioned)
            versioned
        }
        else if(v.modRevision() > versioned.modRevision()) {
            //log.debug("update ${key} rejected ${versioned.modRevision()}")
            v
        } else {
            onAdd?.accept(versioned)
            versioned
        }
    }

}

fun ConcurrentHashMap<String, Versioned>.versionRemove(key : String, versioned : Versioned, onDelete: Consumer<Versioned>?) {

    this.computeIfPresent(key) { k, v ->
        if(v.modRevision() > versioned.modRevision()) {
            v
        } else {
            onDelete?.accept(v)
            null
        }
    }

}

fun ConcurrentHashMap<String, VersionedProp>.versionPropAdd(key : String, versioned : Versioned, onAdd: Consumer<Versioned>? ) {


    this.compute(key) { k, v ->
        if(v == null) {
            onAdd?.accept(versioned)
            VersionedProp(versioned)
        }
        else if(v.modRevision() > versioned.modRevision()) {
            //log.debug("update ${key} rejected ${versioned.modRevision()}")
            v
        } else {
            onAdd?.accept(versioned)
            v.setProp(versioned)
            v
        }
    }

}

fun ConcurrentHashMap<String, VersionedProp>.versionPropRemove(key : String, versioned : Versioned, onDelete: Consumer<Versioned>?) {

    this.computeIfPresent(key) { k, v ->
        if(v.modRevision() > versioned.modRevision()) {
            v
        } else {
            onDelete?.accept(v)
            null
        }
    }

}