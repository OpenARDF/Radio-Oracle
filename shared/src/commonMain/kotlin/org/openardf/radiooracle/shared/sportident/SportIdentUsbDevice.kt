package org.openardf.radiooracle.shared.sportident

/** SPORTident USB bridge identity shared by Android and future desktop SI access. */
object SportIdentUsbDevice {
    const val VENDOR_ID = 4292
    const val PRODUCT_ID = 32778
    const val VENDOR_ID_HEX = "10c4"
    const val PRODUCT_ID_HEX = "800a"

    fun matches(vendorId: Int, productId: Int): Boolean {
        return vendorId == VENDOR_ID && productId == PRODUCT_ID
    }
}
