package com.omni.support

import com.omni.support.ble.utils.AESUtils
import com.omni.support.ble.utils.HexString
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
//        val src = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
//        val src = byteArrayOf(1, 1, 1, 1, 0x2C, 0xBC.toByte(), 0x62, 0x58, 0x96.toByte(), 0x67, 0x42, 0x92.toByte(), 0x01, 0x33, 0x31, 0x41)
//        val src = byteArrayOf(1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val src = byteArrayOf(0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10)

//        val key = byteArrayOf(32, 87, 47, 82, 54, 75, 63, 71, 48, 80, 65, 88, 17, 99, 45, 43)
        val key = byteArrayOf(0x10,0x58,0x21,0xA2.toByte(),0x36,0x4B,0x3F,0x37,0x30,0x50,0x41,0x56,0xA1.toByte(),0x6C,0x2D,0x2B)
//        val key = byteArrayOf(32, 7, 47, 82, 55, 75, 63, 70, 48, 81, 65, 88, 17, 99, 15, 52)
        val encrypt = AESUtils.encrypt(src, key)

        println(HexString.valueOf(encrypt))
    }
}
