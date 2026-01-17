package registry.cerbtk

import org.apache.commons.codec.digest.DigestUtils
import java.util.*

class Block(
    val index: Int,
    val previousHash: String,
    val data: String,
    val proofOfWork: Int,
    val timestamp: Long = Date().time
) {

    val hash: String = calculateHash(index, previousHash, timestamp, data)

    companion object {
        fun calculateHash(index: Int, previousHash: String, timestamp: Long, data: String): String {
            val input = (index.toString() + previousHash + timestamp + data).toByteArray()
            return DigestUtils.sha256Hex(input)
        }
    }
}
