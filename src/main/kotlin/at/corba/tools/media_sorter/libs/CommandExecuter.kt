package at.corba.tools.media_sorter.libs

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Class for executing a command line.
 */
class CommandExecuter(commandLine: String) {
    private val commandParts: Array<String>

    init {
        commandParts = splitCommandLine(commandLine)
    }

    /**
     * Splits a command line into parts while obeying quotation marks.
     * @param   commandLine the command line
     * @return  String-Array
     */
    private fun splitCommandLine(commandLine: String): Array<String> {
        return Regex("""[^\s"]+|"([^"]*)"""")
            .findAll(commandLine)
            .map { it.value.trim('"') }
            .toList()
            .toTypedArray()
    }

    /**
     * Execute a command line application.
     * @return  result of the call
     */
    fun execute(): CommandResult {
        // Start the process
        val process = ProcessBuilder(*commandParts).start()

        // Capture standard output and error output
        val stdOut = StringBuilder()
        val stdErr = StringBuilder()

        // Capture the standard output in a separate thread
        Thread {
            BufferedReader(InputStreamReader(process.inputStream)).forEachLine { line ->
                stdOut.append(line).append("\n")
            }
        }.start()

        // Capture the error output in a separate thread
        Thread {
            BufferedReader(InputStreamReader(process.errorStream)).forEachLine { line ->
                stdErr.append(line).append("\n")
            }
        }.start()

        // Wait for the process to finish
        val exitCode = process.waitFor()

        // Determine if the command was successful
        val success = exitCode == 0

        // Return the result with success flag and both outputs
        return CommandResult(success, stdOut.toString(), stdErr.toString())
    }
}

/**
 * Result of command line call.
 */
data class CommandResult(
    val success: Boolean,
    val standardOutput: String,
    val errorOutput: String
)
