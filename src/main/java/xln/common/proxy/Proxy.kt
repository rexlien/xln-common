package xln.common.proxy

class EndPoint() {

    constructor(name: String, hosts: List<String>) : this() {
        this.name = name
        this.hosts = hosts
    }

    lateinit var name : String
    lateinit var hosts: List<String>

}