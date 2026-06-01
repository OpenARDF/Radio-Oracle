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
    fun si8Or9OrSiacBlock(blockNumber: Int, frame: SportIdentFrame): SportIdentCardBlock? {
        if (frame.command != SportIdentProtocol.GET_SI_CARD8_9_SIAC) {
            return null
        }
        val blockData = when (frame.data.size) {
            SportIdentProtocol.SI_CARD_BLOCK_SIZE -> frame.data
            SportIdentProtocol.SI_CARD_BLOCK_SIZE + SI8_9_SIAC_BLOCK_PREFIX_SIZE ->
                frame.data.copyOfRange(SI8_9_SIAC_BLOCK_PREFIX_SIZE, frame.data.size)
            else -> return null
        }

        return SportIdentCardBlock(
            blockNumber = blockNumber,
            data = blockData
        )
    }

    private const val SI8_9_SIAC_BLOCK_PREFIX_SIZE = 3
}
