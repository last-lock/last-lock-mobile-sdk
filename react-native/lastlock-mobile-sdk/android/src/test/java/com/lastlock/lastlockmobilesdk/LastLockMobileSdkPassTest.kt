package com.lastlock.lastlockmobilesdk

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastLockMobileSdkPassTest {
  private fun group(id: String, certificate: String? = null) = JavaOnlyMap.of(
    "readerGroupIdentifier", id,
    "issuerCAPublicKey", "AwQ=",
    "issuerCACertificateDer", certificate,
  )

  @Test
  fun preservesMultipleGroupsAndOptionalCertificates() {
    val second = group("BQY=")
    val decoded = LastLockMobileSdkModule.decodePass(JavaOnlyMap.of(
      "accessDocument", "AQI=",
      "readerGroups", JavaOnlyArray.of(group("AQI=", "Bwg="), second),
    ))
    assertArrayEquals(byteArrayOf(1, 2), decoded.accessDocument)
    assertEquals(2, decoded.readerGroups.size)
    assertArrayEquals(byteArrayOf(1, 2), decoded.readerGroups[0].readerGroupIdentifier)
    assertArrayEquals(byteArrayOf(5, 6), decoded.readerGroups[1].readerGroupIdentifier)
    assertArrayEquals(byteArrayOf(7, 8), decoded.readerGroups[0].issuerCACertificateDer)
    assertNull(decoded.readerGroups[1].issuerCACertificateDer)
  }

  @Test
  fun acceptsOmittedCertificate() {
    val decoded = LastLockMobileSdkModule.decodePass(JavaOnlyMap.of(
      "accessDocument", "AQI=",
      "readerGroups", JavaOnlyArray.of(JavaOnlyMap.of(
        "readerGroupIdentifier", "AQI=", "issuerCAPublicKey", "AwQ=",
      )),
    ))
    assertNull(decoded.readerGroups.single().issuerCACertificateDer)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsMalformedBase64InLaterGroupInsteadOfDroppingTheGroup() {
    LastLockMobileSdkModule.decodePass(JavaOnlyMap.of(
      "accessDocument", "AQI=",
      "readerGroups", JavaOnlyArray.of(group("AQI="), group("!invalid")),
    ))
  }
}
