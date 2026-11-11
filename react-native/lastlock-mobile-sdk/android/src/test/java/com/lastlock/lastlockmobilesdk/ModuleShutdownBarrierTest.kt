package com.lastlock.lastlockmobilesdk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModuleShutdownBarrierTest {
  /** Exercises both restart entry points while an invalidated module still owns shared resources. */
  @Test
  fun replacementInitializationAndScanBothWaitForInvalidatedModule() = runTest {
    val barrier = ModuleShutdownBarrier(this)
    val release = CompletableDeferred<Unit>()
    val events = mutableListOf<String>()
    barrier.launch { release.await(); events.add("old cleanup") }
    val initialize = async { barrier.await(); events.add("new listeners") }
    val start = async { barrier.await(); events.add("new scan") }
    runCurrent()
    assertFalse(initialize.isCompleted)
    assertFalse(start.isCompleted)
    release.complete(Unit)
    initialize.await()
    start.await()
    assertEquals("old cleanup", events.first())
    assertEquals(setOf("new listeners", "new scan"), events.drop(1).toSet())
  }
}
