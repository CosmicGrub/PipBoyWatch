package com.pipboywatch.shared.log

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PipLog is a singleton (mirrors PhoneStatRelay's own object pattern), so
 * every test re-initializes it with a fresh temp file first — otherwise
 * tests would share state with whichever test ran before them.
 */
class PipLogTest {

    private lateinit var logFile: File

    @Before
    fun setUp() {
        logFile = File.createTempFile("pipboy-test-log", ".log")
        logFile.deleteOnExit()
        PipLog.initWithFile(logFile, "test-device")
    }

    @Test
    fun `a logged line appears in the file with the expected fields`() {
        PipLog.d("MyTag", "hello world")

        val line = logFile.readText().trim()
        val parts = line.split("|", limit = 5)
        assertEquals(5, parts.size)
        assertEquals("test-device", parts[1])
        assertEquals("MyTag", parts[2])
        assertEquals("DEBUG", parts[3])
        assertEquals("hello world", parts[4])
    }

    @Test
    fun `warn and error levels are recorded distinctly`() {
        PipLog.w("MyTag", "a warning")
        PipLog.e("MyTag", "an error")

        val lines = logFile.readText().trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("|WARN|a warning"))
        assertTrue(lines[1].contains("|ERROR|an error"))
    }

    @Test
    fun `a throwable's stack trace is appended to the message`() {
        val exception = RuntimeException("boom")
        PipLog.e("MyTag", "something broke", exception)

        val line = logFile.readText()
        assertTrue(line.contains("something broke"))
        assertTrue(line.contains("RuntimeException"))
        assertTrue(line.contains("boom"))
    }

    @Test
    fun `embedded newlines in the message are stripped to keep one log line per call`() {
        PipLog.d("MyTag", "line one\nline two\r\nline three")

        val lines = logFile.readText().trim().lines()
        assertEquals("a single logical log entry must stay on one physical line", 1, lines.size)
        assertTrue(lines[0].contains("line one line two line three"))
    }

    @Test
    fun `a pipe character embedded in the message does not corrupt the field count`() {
        PipLog.d("MyTag", "steps|activeMinutes|heartRate all in one message")

        val line = logFile.readText().trim()
        val parts = line.split("|", limit = 5)
        assertEquals(5, parts.size)
        assertEquals("steps|activeMinutes|heartRate all in one message", parts[4])
    }

    @Test
    fun `logging to a file whose directory does not exist degrades silently instead of crashing`() {
        // write()'s file append is expected to throw IOException here
        // (the parent directory doesn't exist and PipLog never creates
        // it) — the point of this test is that the exception is caught
        // and swallowed inside write(), not propagated to the caller.
        val unwritableFile = File("/nonexistent-${System.nanoTime()}/sub/pipboy.log")
        PipLog.initWithFile(unwritableFile, "test-device")

        // Must not throw.
        PipLog.d("MyTag", "this should not crash the caller")
    }

    @Test
    fun `the file rotates instead of growing without bound`() {
        // Write well past the 256 KB budget and confirm the file settles
        // back down rather than growing forever.
        val chunk = "x".repeat(2000)
        repeat(200) { PipLog.d("MyTag", chunk) } // ~200 * ~2010 bytes/line > 256 KB

        assertTrue(
            "expected the file to have rotated back down, was ${logFile.length()} bytes",
            logFile.length() < 300 * 1024L
        )
        assertTrue("rotation must not delete everything", logFile.length() > 0)
    }
}
