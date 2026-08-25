package com.pipboywatch.shared.log

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashHandlerTest {

    @Test
    fun `writeCrashSnapshot records device, thread, and the full stack trace`() {
        val file = File.createTempFile("pipboy-test-crash", ".txt").apply { deleteOnExit() }
        val exception = IllegalStateException("something broke")

        CrashHandler.writeCrashSnapshot(file, "test-device", Thread.currentThread(), exception)

        val content = file.readText()
        assertTrue(content.contains("device=test-device"))
        assertTrue(content.contains("thread=${Thread.currentThread().name}"))
        assertTrue(content.contains("IllegalStateException"))
        assertTrue(content.contains("something broke"))
    }

    @Test
    fun `writeCrashSnapshot overwrites rather than accumulates across crashes`() {
        val file = File.createTempFile("pipboy-test-crash", ".txt").apply { deleteOnExit() }

        CrashHandler.writeCrashSnapshot(file, "test-device", Thread.currentThread(), RuntimeException("first crash"))
        CrashHandler.writeCrashSnapshot(file, "test-device", Thread.currentThread(), RuntimeException("second crash"))

        val content = file.readText()
        assertTrue("only the latest crash should be present", content.contains("second crash"))
        assertTrue("the previous crash must not linger", !content.contains("first crash"))
    }

    @Test
    fun `the built handler always chains to the previous handler, even after a successful snapshot`() {
        val file = File.createTempFile("pipboy-test-crash", ".txt").apply { deleteOnExit() }
        var chainedThread: Thread? = null
        var chainedThrowable: Throwable? = null
        val previousHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
            chainedThread = thread
            chainedThrowable = throwable
        }
        val handler = CrashHandler.buildHandler(file, "test-device", previousHandler)
        val originalException = RuntimeException("boom")

        handler.uncaughtException(Thread.currentThread(), originalException)

        assertEquals(Thread.currentThread(), chainedThread)
        assertEquals(originalException, chainedThrowable)
        assertTrue("the snapshot should also have been written", file.readText().contains("boom"))
    }

    @Test
    fun `the previous handler still runs even if snapshotting itself fails`() {
        // A directory in place of the crash file's expected path makes
        // writeText() throw — the handler must not let that prevent the
        // previous handler (the one that actually shows the OS crash
        // dialog and kills the process) from running.
        val brokenPath = File.createTempFile("pipboy-test-crash-dir", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        var chainedCalled = false
        val previousHandler = Thread.UncaughtExceptionHandler { _, _ -> chainedCalled = true }
        val handler = CrashHandler.buildHandler(brokenPath, "test-device", previousHandler)

        // Must not throw out of uncaughtException itself.
        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertTrue("the previous handler must still run even when the snapshot write fails", chainedCalled)
    }

    @Test
    fun `a null previous handler does not crash the built handler`() {
        val file = File.createTempFile("pipboy-test-crash", ".txt").apply { deleteOnExit() }
        val handler = CrashHandler.buildHandler(file, "test-device", previousHandler = null)

        // Must not throw.
        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertTrue(file.readText().contains("boom"))
    }
}
