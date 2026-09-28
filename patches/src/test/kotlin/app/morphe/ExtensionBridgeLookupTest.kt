/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every extension bridge a patch finds by name has exactly one method of that name to find.
 *
 * Several patches look a bridge up by name while patching and rewrite its body, and each needs
 * one match. An extension-only push runs the tests and the lint but, by design, not buildAndroid
 * or the fixture application, so a renamed, removed or overloaded bridge used to fail first in a
 * release receipt or in Morphe Manager. This reads the patch sources and the extension sources
 * and fails naming both, without the desktop CLI.
 */
class ExtensionBridgeLookupTest {
    private val repo = File("..").takeIf { File(it, "extensions").isDirectory } ?: File(".")
    private val patchSources = File(repo, "patches/src/main/kotlin")

    /** Bridges found with the patch that looks each one up, read out of the patch sources. */
    private fun lookups(): List<Triple<String, String, String>> {
        val found = mutableListOf<Triple<String, String, String>>()
        patchSources.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { file ->
            val text = file.readText()
            val classes = Regex("""const val (\w+)\s*=\s*"(Lapp/morphe/extension/[^"]+;)"""")
                .findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
            val handles = Regex("""val (\w+)\s*=\s*mutableClassDefBy\((\w+)\)""").findAll(text)
                .mapNotNull { match -> classes[match.groupValues[2]]?.let { match.groupValues[1] to it } }
                .toMap()
            val lookup = """\.methods\.(?:filter|single|first|singleOrNull|firstOrNull)\s*\{\s*it\.name == """
            handles.forEach { (handle, type) ->
                Regex(Regex.escape(handle) + lookup + "\"(\\w+)\"").findAll(text).forEach {
                    found += Triple(file.name, type, it.groupValues[1])
                }
                // A local helper taking the name, as bridge("readState", ...) and rewrite(...).
                Regex("""fun (\w+)\(name: String[^)]*\)\s*\{\s*val \w+ = """ + Regex.escape(handle) +
                    lookup + "name").findAll(text).forEach { helper ->
                    Regex(Regex.escape(helper.groupValues[1]) + """\(\s*"(\w+)"""").findAll(text).forEach {
                        found += Triple(file.name, type, it.groupValues[1])
                    }
                }
            }
        }
        return found
    }

    private fun declarations(type: String, name: String): Int {
        val path = type.removePrefix("L").removeSuffix(";") + ".java"
        val source = listOf("extensions/tiktok/src/main/java", "extensions/shared/library/src/main/java")
            .map { File(repo, "$it/$path") }.firstOrNull { it.isFile }
            ?: return -1
        val modifiers = "(?:(?:public|protected|private|static|final|synchronized|native|abstract)\\s+)+"
        return Regex("(?m)^\\s*$modifiers[\\w<>\\[\\].,? ]+?\\s+" + Regex.escape(name) + "\\s*\\(")
            .findAll(source.readText()).count()
    }

    @Test
    fun everyBridgeLookedUpByNameHasExactlyOneDeclaration() {
        val found = lookups()
        val problems = found.mapNotNull { (patch, type, name) ->
            when (val count = declarations(type, name)) {
                1 -> null
                -1 -> "$patch looks up $name on $type, and no source for that class was found"
                0 -> "$patch looks up $name on $type, which declares no method of that name"
                else -> "$patch looks up $name on $type, which declares $count methods of that name"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** The scan has to see the bridges it exists for, or a pattern change would pass it empty. */
    @Test
    fun theScanFindsTheKnownBridges() {
        val found = lookups().map { (patch, _, name) -> "$patch:$name" }.toSet()
        val known = listOf(
            "FeedMutePatch.kt:engineSourceId", "FeedMutePatch.kt:setEngineMute",
            "PlaybackSpeedPatch.kt:onFirstFrame", "AutoAdvancePatch.kt:readState",
            "NotInterestedPatch.kt:createCall", "RememberClearDisplayPatch.kt:postClear",
            "RememberClearDisplayPatch.kt:readCurrentAweme", "HideLauncherShortcutsPatch.kt:askHostToRebuild",
        )
        val missing = known.filterNot(found::contains)
        assertTrue("the bridge scan no longer sees: $missing (saw ${found.sorted()})", missing.isEmpty())
    }

    /** Names only match after R8 while it keeps them, which is what these two rules do. */
    @Test
    fun r8KeepsTheExtensionsNames() {
        val rules = File(repo, "extensions/proguard-rules.pro").readText()
        assertTrue("extensions/proguard-rules.pro no longer says -dontobfuscate", "-dontobfuscate" in rules)
        assertTrue("extensions/proguard-rules.pro no longer keeps every app.morphe.extension class",
            Regex("""-keep class app\.morphe\.extension\.\*\* \{\s*\*;\s*}""").containsMatchIn(rules))
    }
}
