/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package utils

import java.io.File

/**
 * Return the git hash, if git is installed.
 */
fun getGitHash(): String {
    val output = File.createTempFile("getGitCommitHash", "")
    try {
        val processBuilder = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText()
            .trim()
            .ifEmpty { "?" }
    } catch (e: Exception) {
        // If git binary is not found, carry on
        return ""
    } finally {
        output.delete()
    }
}

/**
 * Return the latest available domain version from git, if git is installed.
 */
fun getGitVersion(): String? {
    val outputFile = File.createTempFile("gitVersion", "")
    val domainTagPrefix = "domain-v"
    try {
        val processBuilder = ProcessBuilder("git", "describe", "--tags", "--match", "$domainTagPrefix*")
        processBuilder.redirectOutput(outputFile)
        val process = processBuilder.start()
        process.waitFor()
        val output = outputFile.readText().trim()
        val regex = "^$domainTagPrefix([0-9.]+).*\$".toRegex()
        return regex.find(output)
            ?.groups
            ?.get(1)
            ?.value
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    } finally {
        outputFile.delete()
    }
}
