package com.stash.opusplayer.lua

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * A small, reusable "folder of importable .lua scripts" concept, ported
 * from Lumisound's `LuaUserScriptLibrary` (the `enum LuaUserScriptLibrary`
 * in ios/Lumisound/Sources/Services/LuaJSONBridge.swift) — shared by every
 * scriptable feature's community-sharing flow (paste a friend's theme
 * preset, EQ curve, or visualizer style and it just works). Each feature
 * gets its own `subdirectory` name; the scan/import/delete logic is
 * identical, same as the Swift original.
 *
 * Platform difference from the Swift version: bundled scripts there live in
 * `Bundle.main` (the app bundle) under `Resources/<subdirectory>/`; here
 * they live in `assets/<subdirectory>/` and are read via [Context.getAssets].
 * User-imported scripts there live under the app's `Documents` directory;
 * here they live under [Context.getFilesDir] (Android's closest analogue —
 * private, per-app internal storage, not shown to the user in a Files-app
 * sense on either platform without extra work neither app does).
 */
object LuaUserScriptLibrary {

    /** `isBundled == true` scripts are read from `assets/`; `false` ones from [userFile]. */
    data class ScriptRef(
        val id: String,
        val displayName: String,
        val isBundled: Boolean,
        val bundledAssetPath: String? = null,
        val userFile: File? = null
    ) {
        fun readSource(context: Context): String? = when {
            isBundled && bundledAssetPath != null ->
                try {
                    context.assets.open(bundledAssetPath).bufferedReader().use { it.readText() }
                } catch (e: IOException) {
                    null
                }
            userFile != null ->
                try {
                    userFile.readText()
                } catch (e: IOException) {
                    null
                }
            else -> null
        }
    }

    private fun displayName(filenameNoExt: String): String =
        filenameNoExt.split("_", " ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private fun userDirectory(context: Context, subdirectory: String): File =
        File(context.filesDir, subdirectory).apply { mkdirs() }

    /** Bundled example scripts under `assets/<subdirectory>/…lua`. */
    fun bundledScripts(context: Context, subdirectory: String): List<ScriptRef> {
        val names = try {
            context.assets.list(subdirectory)?.filter { it.endsWith(".lua") }
        } catch (e: IOException) {
            null
        } ?: emptyList()
        return names.sorted().map { name ->
            val id = name.removeSuffix(".lua")
            ScriptRef(id = id, displayName = displayName(id), isBundled = true, bundledAssetPath = "$subdirectory/$name")
        }
    }

    /** User-imported scripts under `filesDir/<subdirectory>/`. */
    fun userScripts(context: Context, subdirectory: String): List<ScriptRef> {
        val files = userDirectory(context, subdirectory)
            .listFiles { f -> f.isFile && f.extension.equals("lua", ignoreCase = true) }
            ?: emptyArray()
        return files.sortedBy { it.name }.map { f ->
            val id = f.nameWithoutExtension
            ScriptRef(id = id, displayName = displayName(id), isBundled = false, userFile = f)
        }
    }

    /**
     * Imports raw script source text into `subdirectory`, saved under a
     * sanitized version of [suggestedName] (de-duplicated with a numeric
     * suffix if needed) — the "paste/import a shared script" entry point
     * every community-sharing UI calls.
     */
    fun importScript(context: Context, source: String, suggestedName: String, subdirectory: String): ScriptRef {
        val dir = userDirectory(context, subdirectory)
        val safeName = suggestedName
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .lowercase()
            .ifEmpty { "custom" }
        var candidate = File(dir, "$safeName.lua")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(dir, "${safeName}_$suffix.lua")
            suffix++
        }
        candidate.writeText(source)
        val id = candidate.nameWithoutExtension
        return ScriptRef(id = id, displayName = displayName(id), isBundled = false, userFile = candidate)
    }

    fun deleteUserScript(ref: ScriptRef) {
        if (ref.isBundled) return
        ref.userFile?.delete()
    }
}
