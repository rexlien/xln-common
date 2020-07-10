package xln.common.dist

interface ClusterEvent

class LeaderUp(val leader: Node) : ClusterEvent
class LeaderDown(val leader: Node): ClusterEvent
class NodeUp(val node: Node) : ClusterEvent
class NodeDown(val node: Node) : ClusterEvent

