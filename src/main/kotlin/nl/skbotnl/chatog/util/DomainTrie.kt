package nl.skbotnl.chatog.util

class DomainTrie {
    class Node : HashMap<String, Node>()

    private val root = Node()

    fun insert(domain: String) {
        var node = root
        val labels = domain.split('.').asReversed()
        labels.forEachIndexed { index, label ->
            node =
                if (index == labels.lastIndex) {
                    Node().also { node[label] = it }
                } else {
                    val child = node[label]
                    if (child != null) {
                        if (child.isEmpty()) return // Terminal node, subdomains already handled
                        child
                    } else {
                        Node().also { node[label] = it }
                    }
                }
        }
    }

    fun contains(domain: String): Boolean {
        var node = root
        for (label in domain.split('.').asReversed()) {
            node = node[label] ?: return false
            if (node.isEmpty()) return true
        }
        return false
    }

    fun clear() = root.clear()
}
