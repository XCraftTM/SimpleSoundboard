package org.kvxd.simplesoundboard

import net.minecraft.util.Util
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

object YtDlpManager {

    private val isWindows = Util.getOperatingSystem().getName().contains("windows", ignoreCase = true)
    private val binaryName = if (isWindows) "yt-dlp.exe" else "yt-dlp"
    private val downloadUrl = if (isWindows)
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    else
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

    private val ffmpegBinaryName = if (isWindows) "ffmpeg.exe" else "ffmpeg"
    private val ffmpegDownloadUrl = if (isWindows)
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip"
    else
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl-shared.tar.xz"

    private fun binFile(): File {
        if (!SimpleSoundboardClient.modDir.exists()) SimpleSoundboardClient.modDir.mkdirs()
        return File(SimpleSoundboardClient.modDir, binaryName)
    }

    private fun ffmpegBinFile(): File {
        if (!SimpleSoundboardClient.modDir.exists()) SimpleSoundboardClient.modDir.mkdirs()
        return File(SimpleSoundboardClient.modDir, ffmpegBinaryName)
    }

    @Synchronized
    fun ensureBinariesPresent(): Boolean {
        return ensureYtDlpPresent() && ensureFfmpegPresent()
    }

    @Synchronized
    fun ensureYtDlpPresent(): Boolean {
        val bin = binFile()
        if (bin.exists() && bin.canExecute()) return true

        return try {
            downloadBinary(downloadUrl, bin)
            bin.setExecutable(true, false)
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    @Synchronized
    fun ensureFfmpegPresent(): Boolean {
        val bin = ffmpegBinFile()
        if (bin.exists() && bin.canExecute()) return true

        return try {
            val archiveName = if (isWindows) "ffmpeg.zip" else "ffmpeg.tar.xz"
            val archiveFile = File(SimpleSoundboardClient.modDir, archiveName)
            downloadBinary(ffmpegDownloadUrl, archiveFile)

            if (isWindows) {
                extractZip(archiveFile, SimpleSoundboardClient.modDir)
            } else {
                extractTarXz(archiveFile, SimpleSoundboardClient.modDir)
            }

            archiveFile.delete()
            bin.setExecutable(true, false)
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        java.util.zip.ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                if (!entry.isDirectory && (name.contains("/bin/") || name.startsWith("bin/"))) {
                    val fileName = name.substringAfterLast('/')
                    val outputFile = File(destDir, fileName)
                    zip.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun extractTarXz(archiveFile: File, destDir: File) {
        try {
            // Find the root directory name in the tarball
            val pbList = ProcessBuilder("tar", "-tJf", archiveFile.absolutePath)
            val procList = pbList.start()
            val firstEntry = BufferedReader(InputStreamReader(procList.inputStream)).readLine()
            val rootDir = firstEntry?.substringBefore('/') ?: ""
            procList.waitFor()

            if (rootDir.isNotEmpty()) {
                // Extract everything from the 'bin' directory to destDir, flattening it
                val pb = ProcessBuilder(
                    "tar", "-xJf", archiveFile.absolutePath,
                    "--strip-components=2",
                    "-C", destDir.absolutePath,
                    "$rootDir/bin"
                )
                pb.start().waitFor(2, TimeUnit.MINUTES)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(Exception::class)
    private fun downloadBinary(urlStr: String, dest: File) {
        val url = URI(urlStr).toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SimpleSoundboard-Downloader")
        }

        conn.connect()
        val code = conn.responseCode
        if (code >= 400) {
            conn.disconnect()
            throw RuntimeException("Failed to download from $urlStr: HTTP $code")
        }

        BufferedInputStream(conn.inputStream).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        conn.disconnect()
    }

    fun downloadUrlIntoSoundDir(
        url: String,
        audioOnly: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): Pair<Boolean, String> {
        if (url.isBlank()) return Pair(false, "message.simplesoundboard.empty_url")

        if (!ensureBinariesPresent()) {
            return Pair(false, "message.simplesoundboard.binaries_missing")
        }

        val bin = binFile()
        val ffmpegBin = ffmpegBinFile()
        val soundDir = SimpleSoundboardClient.soundDir.also { if (!it.exists()) it.mkdirs() }

        val outputPattern = File(soundDir, "%(title)s.%(ext)s").absolutePath
        val args = mutableListOf<String>()

        args.add(bin.absolutePath)
        args.addAll(listOf("--ffmpeg-location", ffmpegBin.absolutePath))

        if (audioOnly) {
            args.addAll(listOf("-x", "--audio-format", "mp3"))
        }

        args.addAll(listOf("-o", outputPattern, url))

        try {
            val pb = ProcessBuilder(args).apply {
                directory(soundDir)
                redirectErrorStream(true)
            }

            val proc = pb.start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(proc.inputStream)).use { r ->
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    output.appendLine(line)

                    onProgress(line!!)
                }
            }

            val finished = proc.waitFor(10, TimeUnit.MINUTES)
            if (!finished) {
                proc.destroyForcibly()
                return Pair(false, "message.simplesoundboard.youtube.timeout")
            }

            val exit = proc.exitValue()
            val outStr = output.toString()
            return if (exit == 0) {
                Pair(true, outStr.ifBlank { "message.simplesoundboard.download_completed" })
            } else {
                Pair(false, "message.simplesoundboard.youtube.exit_code")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return Pair(false, "Exception: ${t.message}")
        }
    }

}