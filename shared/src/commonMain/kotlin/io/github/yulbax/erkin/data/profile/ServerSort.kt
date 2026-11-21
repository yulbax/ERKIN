package io.github.yulbax.erkin.data.profile

enum class ServerSort(val wire: String) {
    PING("ping"),
    NAME("name"),
    MANUAL("manual");

    companion object {
        val DEFAULT = PING

        fun fromWire(wire: String): ServerSort? = entries.firstOrNull { it.wire == wire }
    }
}

object ServerNameOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val x = a.sortKey()
        val y = b.sortKey()
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            if (x[i].isDigit() && y[j].isDigit()) {
                val numberEnd = x.digitsEnd(i)
                val otherEnd = y.digitsEnd(j)
                val number = x.substring(i, numberEnd).trimStart('0')
                val other = y.substring(j, otherEnd).trimStart('0')
                if (number.length != other.length) return number.length - other.length
                number.compareTo(other).let { if (it != 0) return it }
                i = numberEnd
                j = otherEnd
            } else {
                x[i].lowercaseChar().compareTo(y[j].lowercaseChar()).let { if (it != 0) return it }
                i++
                j++
            }
        }
        return (x.length - i) - (y.length - j)
    }

    private fun String.sortKey(): String = dropWhile { !it.isLetterOrDigit() }

    private fun String.digitsEnd(start: Int): Int {
        var end = start
        while (end < length && this[end].isDigit()) end++
        return end
    }
}
