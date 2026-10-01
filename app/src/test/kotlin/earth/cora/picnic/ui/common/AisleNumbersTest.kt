package earth.cora.picnic.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AisleNumbersTest {
    @Test
    fun readsTheStoresOwnAisleNumber() {
        assertEquals(2, storeAisleNumber("Aisle 2 - Baking & spices"))
        assertEquals(12, storeAisleNumber("12 Frozen"))
        assertEquals(7, storeAisleNumber("aisle 7"))
        assertEquals(4, storeAisleNumber("Allée 4 : conserves"))
        assertEquals(3, storeAisleNumber("#3 Snacks"))
        assertEquals(5, storeAisleNumber("Aisle5 - Snacks & drinks"))
    }

    @Test
    fun namesWithoutANumberGetNone() {
        assertNull(storeAisleNumber("Produce"))
        assertNull(storeAisleNumber("Dairy & eggs"))
        assertNull(storeAisleNumber("1% milk fridge"))
        assertNull(storeAisleNumber("Aisle of wonders"))
    }
}
