package at.corba.tools.media_sorter.logic

import at.corba.tools.media_sorter.libs.CommandExecuter
import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.ImageProcessingException
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Component
class MediaSorterLogic
{
    /** The logger */
    private val log = KotlinLogging.logger {}

    // define formatters
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyyMM")
    private val yearMonthDayFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Sorts
     * @param inputDirectory    source directory
     * @param outputDirectory   destination directory
     * @param testOnly          when true, just log it. when false: do the move
     */
    fun sort(inputDirectory: File, outputDirectory: File, testOnly: Boolean) {
        // Logging the test mode|
        log.info {
            var logText = "*** Testmode is $testOnly, "
            if (testOnly)
                logText += "therefore I will not perform real action"
            else
                logText += "so I will perform this actions"

            logText
        }

        // delete unnecessary files
        inputDirectory
            .listFiles()
            ?.filter { fileEntry -> fileEntry.isFile }
            ?.forEach { fileEntry ->
                val extension = fileEntry.extension.uppercase()
                if (extension == "AAE") {
                    performFileDelete(fileEntry, testOnly)
                }
            }

        // process remaining photos and videos
        inputDirectory
            .listFiles()
            ?.filter { fileEntry -> fileEntry.isFile }
            ?.filter { fileEntry -> isPhoto(fileEntry) || isVideo(fileEntry) }
            ?.forEach { fileEntry ->
                val mediaTS = getCreationDate(fileEntry)
                if (mediaTS != null) {
                    val yearDirectory = mediaTS.format(yearFormatter)
                    val yearMonthDirectory = mediaTS.format(yearMonthFormatter)
                    val yearMonthDayDirectory = mediaTS.format(yearMonthDayFormatter)
                    val action = getAction(fileEntry)

                    when (action) {
                        Action.MOVE -> {
                            performFileMove(fileEntry, outputDirectory,
                                "${yearDirectory}/${yearMonthDirectory}/${yearMonthDayDirectory}",
                                testOnly)
                        }
                        Action.MOVE_AND_CONVERT_HEIC -> {
                            performFileConvertHeicAndMove(fileEntry, outputDirectory,
                                "${yearDirectory}/${yearMonthDirectory}/${yearMonthDayDirectory}",
                                testOnly)
                        }
                    }
                }
                else {
                    log.warn("Could not get creation date for ${fileEntry.absolutePath}")
                }
            }
    }

    /**
     * Is the file a photo (extension is JPG)?
     * @param   file the file
     * @return  true if the file is a photo
     */
    private fun isPhoto(file: File) : Boolean {
        val extension = file.extension.uppercase()
        return (listOf<String>("JPG", "JPEG", "HEIC", "PNG").contains(extension))
    }

    /**
     * Is the file a video (extension is MP4/MOV)?
     * @param   file the file
     * @return  true if the file is a video
     */
    private fun isVideo(file: File) : Boolean {
        val extension = file.extension.uppercase()
        return (listOf<String>("MP4", "MOV").contains(extension))
    }

    /**
     * Get Action for a given filetype.
     * @param   file the file
     * @return  Action for the given file extension
     */
    private fun getAction(file: File) : Action {
        val extension = file.extension.uppercase()
        return when (extension) {
            "HEIC" -> Action.MOVE_AND_CONVERT_HEIC
            else -> Action.MOVE
        }
    }

    /**
     * Get the creation time for the given file.
     * First try to get it from the metadata, if not present get it from the file system.
     * @param   file the file
     * @return  creation time
     */
    private fun getCreationDate(file: File) : LocalDateTime? {
        var date : LocalDateTime?
        if (isPhoto(file)) {
            date = geMetadataIFD0Date(file)
            if (date == null) {
                date = geMetadataISubIFDDate(file)
            }
        }
        else if (isVideo(file)) {
            date = geMetadataQuicktimeMetadataDate(file)
        }
        else {
            log.debug { "Unknown file: ${file.absoluteFile}" }
            return null
        }

        if (date == null) {
            log.warn { "*** No meta data entry: ${file.absoluteFile}" }
            date = getFileDate(file)
        }
        return date
    }

    /**
     * Log meta entries for a given file.
     * @param   file the file
     */
    private fun listMetaEntries(file: File) {
        log.info { file.absoluteFile }
        ImageMetadataReader
            .readMetadata(file)
            .directories
            .forEach { directory ->
                log.info { "- $directory.name" }
                for (tag in directory.tags) {
                    log.info("  - ${directory.getName()}, ${tag.getTagName()}, ${tag.getDescription()}")
                }
            }
    }

    /**
     * Test a given file (log meta entries and creation date).
     * @param   file the file
     */
    fun testFile(testFile: File) {
        listMetaEntries(testFile)
        val date = getCreationDate(testFile)
        log.info { date }
    }

    /**
     * Get date from the Exif IFD0 directory.
     * @param   file the file
     * @return  date
     */
    private fun geMetadataIFD0Date(file: File) : LocalDateTime? {
        try {
            val directory = ImageMetadataReader
                .readMetadata(file)
                .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                ?: return null
            val date = directory
                .getDate(ExifIFD0Directory.TAG_DATETIME, TimeZone.getDefault())
                ?: return null

            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
        }
        catch (e: ImageProcessingException) {
            log.error("Error in processing file ${file.absolutePath}: ${e.message}")
            throw e
        }
    }

    /**
     * Get date from the Exif SubIFD directory.
     * @param   file the file
     * @return  date
     */
    private fun geMetadataISubIFDDate(file: File) : LocalDateTime? {
        val directory = ImageMetadataReader
            .readMetadata(file)
            .getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            ?: return null
        val date = directory
            .getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, TimeZone.getDefault())
            ?: return null

        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
    }

    /**
     * Get date from the Quicktime directory.
     * @param   file the file
     * @return  date
     */
    private fun geMetadataQuicktimeMetadataDate(file: File) : LocalDateTime? {
        val directory = ImageMetadataReader
            .readMetadata(file)
            .getFirstDirectoryOfType(QuickTimeMetadataDirectory::class.java)
            ?: return null
        val date = directory
            .getDate(QuickTimeMetadataDirectory.TAG_CREATION_DATE, TimeZone.getDefault())
            ?: return null

        return LocalDateTime
            .ofInstant(date.toInstant(), ZoneId.systemDefault())
            .plusHours(1)
    }

    /**
     * Get file creation date.
     * @param   file the file
     * @return  date
     */
    private fun getFileDate(file: File) : LocalDateTime {
        val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        val creationTS = LocalDateTime.ofInstant(attr.creationTime().toInstant(), ZoneId.systemDefault())
        val lastModifiedTS = LocalDateTime.ofInstant(attr.lastModifiedTime().toInstant(), ZoneId.systemDefault())
        return if (creationTS < lastModifiedTS) {
            creationTS.plusHours(1)
        } else {
            lastModifiedTS.plusHours(1)
        }
    }

    /**
     * Move a file.
     * @param source            source file
     * @param outputDir         destination directory
     * @param monthDirectory    directory name for month
     * @param testOnly          when true, just log it. when false: do the move
     */
    private fun performFileMove(source: File, outputDir: File, monthDirectory: String, testOnly: Boolean) {
        val destinationDirectory = File(outputDir, monthDirectory)
        makeDirectory(destinationDirectory, testOnly)

        val destination = File(destinationDirectory, source.name)
        moveFile(source, destination, testOnly)
    }

    /**
     * Move a file.
     * @param source            source file
     * @param outputDir         destination directory
     * @param monthDirectory    directory name for month
     * @param testOnly          when true, just log it. when false: do the move
     */
    private fun performFileConvertHeicAndMove(source: File, outputDir: File, monthDirectory: String, testOnly: Boolean) {
        val destinationDirectory = File(outputDir, monthDirectory)
        makeDirectory(destinationDirectory, testOnly)

        var newSource = source
        if (!testOnly) {
            newSource = convertHeicToJpg(source)
        }

        val destination = File(destinationDirectory, newSource.name)
        moveFile(newSource, destination, testOnly)
    }

    /**
     * Deletes a file.
     * @param source            source file
     * @param testOnly          when true, just log it. when false: do the move
     */
    private fun performFileDelete(source: File, testOnly: Boolean) {
        log.info { "Delete file ${source.path}" }
        if (!testOnly) {
            try {
                source.delete()
            }
            catch (e: Exception) {
                log.error { e.message }
            }
        }
    }

    /**
     * Create a directory.
     * @param directory directory to create
     * @param testOnly  when true, just log it. when false: create it
     */
    private fun makeDirectory(directory: File, testOnly: Boolean) {
        if (!directory.exists()) {
            log.info { "Make directory ${directory.path}" }
            if (!testOnly) {
                if (!directory.mkdirs()) {
                    log.error { "Could not create directory $directory"}
                }
            }
        }
    }

    /**
     * Move a file.
     * @param source        source file
     * @param destination   destination file
     */
    private fun moveFile(source: File, destination: File, testOnly: Boolean) {
        log.info { "Move file from ${source.path} to ${destination.path}" }
        if (!testOnly) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun convertHeicToJpg(source: File): File {
        val command = CommandExecuter(
            "magick mogrify -format JPG \"${source.absolutePath}\"")
        val result = command.execute()
        if (!result.success) {
            log.error { "- Could not convert HEIC to JPG: ${source.absolutePath}" }
            throw IllegalStateException(
                "Could not convert HEIC to JPG: ${source.absolutePath} ${result.errorOutput}")
        }
        val destination = changeFileExtension(source, "jpg")
        if (!destination.exists() || (destination.length() == 0L)) {
            log.error { "- Could not convert HEIC to JPG: ${source.absolutePath}" }
            throw IllegalStateException("Could not convert HEIC to JPG: ${source.absolutePath}")
        }
        else {
            source.delete()
            return destination
        }
    }

    private fun changeFileExtension(file: File, newExtension: String): File {
        return File(file.parent, "${file.nameWithoutExtension}.$newExtension")
    }

}

