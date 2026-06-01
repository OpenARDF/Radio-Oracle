package org.openardf.radiooracle.shared.sportident

data class SportIdentCardBlock(
    val blockNumber: Int,
    val data: ByteArray
) {
    init {
        require(data.size == SportIdentProtocol.SI_CARD_BLOCK_SIZE) {
            "SI card block must contain ${SportIdentProtocol.SI_CARD_BLOCK_SIZE} bytes."
        }
    }
}

object SportIdentCardBlockParser {
    fun si6Block(blockNumber: Int, frame: SportIdentFrame): SportIdentCardBlock? {
        if (frame.command != SportIdentProtocol.GET_SI_CARD6) {
            return null
        }
        return blockWithOptionalPrefix(blockNumber, frame.data)
    }

    fun si8Or9OrSiacBlock(blockNumber: Int, frame: SportIdentFrame): SportIdentCardBlock? {
        if (frame.command != SportIdentProtocol.GET_SI_CARD8_9_SIAC) {
            return null
        }
        return blockWithOptionalPrefix(blockNumber, frame.data)
    }

    private fun blockWithOptionalPrefix(blockNumber: Int, data: ByteArray): SportIdentCardBlock? {
        val blockData = when (data.size) {
            SportIdentProtocol.SI_CARD_BLOCK_SIZE -> data
            SportIdentProtocol.SI_CARD_BLOCK_SIZE + SI8_9_SIAC_BLOCK_PREFIX_SIZE ->
                data.copyOfRange(SI_BLOCK_PREFIX_SIZE, data.size)
            else -> return null
        }

        return SportIdentCardBlock(
            blockNumber = blockNumber,
            data = blockData
        )
    }

    private const val SI8_9_SIAC_BLOCK_PREFIX_SIZE = 3
    private const val SI_BLOCK_PREFIX_SIZE = 3
}
