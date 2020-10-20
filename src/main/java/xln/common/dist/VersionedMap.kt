package xln.common.dist

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.Consumer

fun <T: Versioned> ConcurrentHashMap<String, T>.versionAdd(key : String, versioned : T, onAdd: Consumer<Versioned>? = null) {

    this.compute(key) { k, v ->
        if(v == null) {
            onAdd?.accept(versioned)
            versioned
        }
        else if(v.getModRevision() > versioned.getModRevision()) {
            //log.debug("update ${key} rejected ${versioned.modRevision()}")
            v
        } else {
            onAdd?.accept(versioned)
            versioned
        }
    }

}

fun <T: Versioned> ConcurrentHashMap<String, T>.versionRemove(key : String, versioned : T, onDelete: Consumer<Versioned>?) {

    this.computeIfPresent(key) { k, v ->
        if(v.getModRevision() > versioned.getModRevision()) {
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
        else if(v.getModRevision() > versioned.getModRevision()) {
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
        if(v.getModRevision() > versioned.getModRevision()) {
            v
        } else {
            onDelete?.accept(v)
            null
        }
    }

}