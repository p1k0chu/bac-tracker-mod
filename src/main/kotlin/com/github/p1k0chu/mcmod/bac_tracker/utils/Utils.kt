package com.github.p1k0chu.mcmod.bac_tracker.utils

import com.github.p1k0chu.mcmod.bac_tracker.Main.APP_NAME
import com.github.p1k0chu.mcmod.bac_tracker.Main.GSON
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import java.util.regex.Pattern
import kotlin.collections.firstOrNull

object Utils {
    private val rangeRegex: Pattern = Pattern.compile("(?<sL>\\D+)(?<sN>\\d*):(?<eL>\\D+)", Pattern.CASE_INSENSITIVE)
    private val googleSheetUrlRegex = Pattern.compile("https://docs\\.google\\.com/spreadsheets/d/(?<id>.*)/edit.*")

    // used for parsing from advancement json files
    val minecraftTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).withZone(ZoneId.systemDefault())

    /**
     * @return the id from the url
     * @throws IllegalArgumentException if [str] doesn't match the url regex ([googleSheetUrlRegex])
     */
    fun parseSheetUrl(str: String): String {
        val m = googleSheetUrlRegex.matcher(str)
        return if (m.find()) {
            m.group("id")
        } else {
            throw IllegalArgumentException("url doesn't match regex")
        }
    }

    /**
     * @param cell  cell range like "A1:A" (regex for cell must be \D+\d*:\D+)
     * @param index index, like 2
     * @return cell range, like A3:A, or null if cell doesn't match regex
     */
    fun moveRangeDownBy(
        cell: String,
        index: Int,
    ): String? {
        val m = rangeRegex.matcher(cell)
        if (!m.find()) {
            return null
        }

        val startLetter: String = m.group("sL") ?: return null
        val endLetter: String = m.group("eL") ?: return null
        val startNumber: Int = m.group("sN").toIntOrNull() ?: 1

        return "$startLetter${startNumber + index}:$endLetter"
    }

    /**
     * @param player uuid of the player
     * @return url of profile picture as excel =IMAGE(stuff)
     */
    fun getProfilePictureByUuid(player: String): String {
        return "=IMAGE(\"https://crafatar.com/avatars/$player?size=16&overlay\")"
    }

    fun buildSheet(credPath: Path): Sheets {
        val email = credPath.toFile().reader().use { reader ->
            val json: JsonObject = GSON.fromJson(reader, JsonObject::class.java)

            json.get("client_email").asString
        }

        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(
                credPath
                    .toFile()
                    .inputStream()
                    .use { stream -> GoogleCredentials.fromStream(stream) }
                    .createScoped(mutableSetOf<String?>(SheetsScopes.SPREADSHEETS))
                    .createDelegated(email),
            ),
        ).setApplicationName(APP_NAME)
            .build()
    }

    /** creates a directory if it doesn't exist
     * @throws IOException when failed to create a directory
     * @throws SecurityException from java.io.File.mkdir() */
    @Throws(IOException::class, SecurityException::class)
    fun makeSureDirectoryExists(path: Path) {
        if (!path.toFile().isDirectory && !path.toFile().mkdir()) {
            throw IOException("failed to create new directory: ${path.toAbsolutePath().normalize()}")
        }
    }

    /** @param advJson advancement json object */
    fun findLatestCriteriaObtainedDate(advJson: JsonObject): Instant? {
        var max: Instant? = null

        advJson["criteria"].getAsJsonObject().asMap().forEach { (_, time) ->
            val instant = minecraftTimeFormatter.parse(time.asString) { temporal: TemporalAccessor? ->
                Instant.from(temporal)
            }

            if (max?.isBefore(instant) != false) {
                max = instant
            }
        }
        return max
    }

    /** single column value range will be just list of lists with one element.
     * filters out null and maps it to be 1-dimensional list */
    @Suppress("UsePropertyAccessSyntax") // IDK why but using property access breaks everything
    fun singleColumnValueRange(range: ValueRange): List<String> = range.getValues().mapNotNull { it?.firstOrNull()?.toString() }
}
