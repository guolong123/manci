package org.manci

import java.util.concurrent.ConcurrentHashMap

class Group implements Serializable{
    transient String name
    transient ConcurrentHashMap<String, Object> groups = [:] as ConcurrentHashMap<String, Object>
    transient ConcurrentHashMap<String, Closure> stages = [:] as ConcurrentHashMap

    Group() {
        this.name = name
    }
    @NonCPS
    def getStage(String name) {
        return stages.get(name)
    }
    @NonCPS
    def getGroup(String name) {
        return groups.get(name)
    }

    @NonCPS
    def addStage(String name, Closure closure) {
        stages.put(name, closure)
    }
    @NonCPS
    def addGroup(String name, Closure closure) {
        groups.put(name, closure)
    }
    @NonCPS
    @Override
    String toString() {
        return this.groups.toString()
    }

}