package com.lastlock.lastlockmobilesdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shared by module generations across React Native reloads. */
internal class ModuleShutdownBarrier(private val scope: CoroutineScope) {
  private var pending: Job? = null

  /** Queues cleanup after earlier module generations, independently of an invalidated module's scope. */
  @Synchronized
  fun launch(cleanup: suspend () -> Unit) {
    val previous = pending
    pending = scope.launch {
      previous?.join()
      cleanup()
    }
  }

  /** Waits for pending cleanup without cancelling it if this caller stops waiting. */
  suspend fun await() {
    val cleanup = synchronized(this) { pending }
    cleanup?.join()
  }
}
