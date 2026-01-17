package registry.cerbtk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Blockchain {
    private const val difficulty = 1
    private val storageEnabled: Boolean by lazy {
        System.getProperty("cerbtk.storageEnabled", "true").toBoolean()
    }
    private val storagePath: Path by lazy {
        Paths.get(System.getProperty("cerbtk.storagePath", "data/chain.json"))
    }
    private val mapper = jacksonObjectMapper()
    private val chainLock = Any()

    val chain = mutableListOf<Block>()
    val latestBlock: Block
        get() = chain.last()

    init {
        chain.addAll(loadChain())
    }

    fun mineBlock(data: String): Block {
        val proofOfWork = generateProofOfWork(latestBlock.proofOfWork)
        val block = Block(chain.size, latestBlock.hash, data, proofOfWork)

        addNewBlock(block)

        return latestBlock
    }

    fun findBlockByHash(hash: String): Block? =
        chain.firstOrNull { it.hash == hash }

    fun isChainValid(): Boolean {
        if (chain.isEmpty()) return false
        val genesis = chain.first()
        if (genesis.index != 0) return false
        if (genesis.previousHash != "0") return false
        if (genesis.hash != Block.calculateHash(
                genesis.index,
                genesis.previousHash,
                genesis.timestamp,
                genesis.data
            )
        ) {
            return false
        }

        var previous = genesis
        for (i in 1 until chain.size) {
            val current = chain[i]
            if (current.index != previous.index + 1) return false
            if (current.previousHash != previous.hash) return false
            if (current.hash != Block.calculateHash(
                    current.index,
                    current.previousHash,
                    current.timestamp,
                    current.data
                )
            ) {
                return false
            }
            if (!isProofOfWorkValid(current.proofOfWork, previous.proofOfWork)) return false
            previous = current
        }
        return true
    }

    fun resetForTest() {
        chain.clear()
        chain.add(genesisBlock())
    }

    private fun addNewBlock(block: Block) {
        synchronized(chainLock) {
            if (isNewBlockValid(block)) {
                chain.add(block)
                persistChain(chain)
            }
        }
    }

    private fun generateProofOfWork(previousPow: Int): Int {
        var proof = previousPow + 1
        val nonce = 8 * difficulty
        while ((proof + previousPow) % nonce != 0) {
            proof += 1
        }
        return proof
    }

    private fun isNewBlockValid(newBlock: Block): Boolean {
        val previousBlock = latestBlock
        if (newBlock.index != previousBlock.index + 1) return false
        if (newBlock.previousHash != previousBlock.hash) return false
        if (newBlock.hash != Block.calculateHash(
                newBlock.index,
                newBlock.previousHash,
                newBlock.timestamp,
                newBlock.data
            )
        ) {
            return false
        }
        return isProofOfWorkValid(newBlock.proofOfWork, previousBlock.proofOfWork)
    }

    private fun isProofOfWorkValid(proof: Int, previousPow: Int): Boolean {
        val nonce = 8 * difficulty
        return ((proof + previousPow) % nonce == 0)
    }

    private fun genesisBlock(): Block =
        Block(0, "0", "Genesis block", 0)

    private fun loadChain(): List<Block> {
        if (!storageEnabled) return listOf(genesisBlock())

        if (!Files.exists(storagePath)) {
            val genesis = genesisBlock()
            persistChain(listOf(genesis))
            return listOf(genesis)
        }

        return try {
            val blocks = mapper.readValue(storagePath.toFile(), Array<Block>::class.java).toList()
            if (blocks.isEmpty()) listOf(genesisBlock()) else blocks
        } catch (ex: Exception) {
            val genesis = genesisBlock()
            persistChain(listOf(genesis))
            listOf(genesis)
        }
    }

    private fun persistChain(blocks: List<Block>) {
        if (!storageEnabled) return
        val parent = storagePath.parent
        if (parent != null) Files.createDirectories(parent)
        mapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), blocks)
    }
}
