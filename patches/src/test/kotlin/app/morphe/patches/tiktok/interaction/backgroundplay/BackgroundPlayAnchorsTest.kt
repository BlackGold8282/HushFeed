/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.interaction.backgroundplay

import app.morphe.Fixtures
import app.morphe.takes
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Keep playing in the background changes, held to each declared build: the one lazy that
 * reads the mode hands back the int it hooks, and every place TikTok loads the remembered
 * switch's key either reads it through a hook or stores it, so no read is left on TikTok's say.
 */
class BackgroundPlayAnchorsTest {
    private fun Instruction.loads(key: String) =
        (opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO) &&
            getReference<StringReference>()?.string == key

    @Test
    fun `the mode read and the remembered switch reads resolve on each build`() {
        Fixtures.forEachDeclared { apk ->
            val classes = HashMap<String, ClassDef>()
            val container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault())
            for (entry in container.dexEntryNames) {
                for (classDef in container.getEntry(entry)!!.dexFile.classes) classes.putIfAbsent(classDef.type, classDef)
            }
            val version = Fixtures.versionOf(apk)
            val methods: List<Method> = classes.values.flatMap { it.methods }.filter { it.implementation != null }

            val gate = classes.values.flatMap { classDef -> classDef.methods.filter { BackgroundPlayGateReadFingerprint.takes(it, classDef) } }
            assertEquals("$version: the gate read takes ${gate.map { it.definingClass }}", 1, gate.size)
            val gateBody = gate.single().implementation!!.instructions.toList()
            val key = gateBody.indexOfFirst { it.loads(GATE_KEY) }
            val read = (key + 1 until gateBody.size).first { gateBody[it].getReference<MethodReference>()?.returnType == "I" }
            val result = gateBody[read + 1]
            assertEquals("$version: the gate's int is not kept", Opcode.MOVE_RESULT, result.opcode)
            // The int the patch changes is the one the lazy boxes and hands back.
            val box = gateBody[read + 2]
            assertEquals("$version: the gate's int is not what the lazy returns",
                "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;", box.getReference<MethodReference>().toString())
            assertEquals((result as OneRegisterInstruction).registerA, (box as FiveRegisterInstruction).registerC)

            // The fingerprint's candidates: the key's string plus a Keva getBoolean anywhere.
            val candidates = methods.filter { method ->
                val body = method.implementation!!.instructions
                body.any { it.loads(REMEMBERED_KEY) } && body.any {
                    val call = it.getReference<MethodReference>()
                    call?.definingClass == KEVA && call.name == "getBoolean"
                }
            }
            var hooked = 0
            for (method in candidates) {
                val body = method.implementation!!.instructions.toList()
                for (at in body.indices.filter { body.readsKevaBoolean(it, REMEMBERED_KEY) }) {
                    assertEquals("$version: a read in ${method.definingClass}->${method.name} is not kept",
                        Opcode.MOVE_RESULT, body.getOrNull(at + 1)?.opcode)
                    hooked++
                }
            }
            assertTrue("$version: $hooked remembered reads, the controller and per-video checks need 2", hooked >= 2)

            // Every other load of the key feeds a store, not a read the patch would miss.
            val unhooked = methods.flatMap { method ->
                val body = method.implementation!!.instructions.toList()
                body.indices.filter { at ->
                    body[at].loads(REMEMBERED_KEY) &&
                        (at + 1..minOf(at + 3, body.size - 1)).none { body.readsKevaBoolean(it, REMEMBERED_KEY) } &&
                        (at + 1..minOf(at + 3, body.size - 1)).none { body[it].getReference<MethodReference>()?.let { call ->
                            call.definingClass == KEVA && call.name.startsWith("store")
                        } == true }
                }.map { "${method.definingClass}->${method.name}@$it" }
            }
            assertTrue("$version: the remembered switch is read where no hook is: $unhooked", unhooked.isEmpty())
        }
    }

    @Test
    fun `a read counts only when the key goes into the key register just before`() {
        val getBoolean = ImmutableMethodReference(KEVA, "getBoolean", listOf("Ljava/lang/String;", "Z"), "Z")
        fun key(register: Int, value: String = REMEMBERED_KEY) =
            ImmutableInstruction21c(Opcode.CONST_STRING, register, ImmutableStringReference(value))
        fun default(register: Int) = ImmutableInstruction11n(Opcode.CONST_4, register, 0)
        fun call(keyRegister: Int, reference: ImmutableMethodReference = getBoolean, opcode: Opcode = Opcode.INVOKE_VIRTUAL) =
            ImmutableInstruction35c(opcode, 3, 0, keyRegister, 2, 0, 0, reference)

        assertTrue(listOf(key(1), call(1)).readsKevaBoolean(1, REMEMBERED_KEY))
        assertTrue(listOf(key(1), default(2), call(1)).readsKevaBoolean(2, REMEMBERED_KEY))
        assertFalse("another key", listOf(key(1, "bg_play_toast"), call(1)).readsKevaBoolean(1, REMEMBERED_KEY))
        assertFalse("the key in another register", listOf(key(4), call(1)).readsKevaBoolean(1, REMEMBERED_KEY))
        assertFalse("the key too far back",
            listOf(key(1), default(2), default(5), default(6), call(1)).readsKevaBoolean(4, REMEMBERED_KEY))
        assertFalse("a store", listOf(key(1), call(1, ImmutableMethodReference(KEVA, "storeBoolean",
            listOf("Ljava/lang/String;", "Z"), "V"))).readsKevaBoolean(1, REMEMBERED_KEY))
        assertFalse("another class's getBoolean", listOf(key(1), call(1, ImmutableMethodReference(
            "Landroid/content/SharedPreferences;", "getBoolean", listOf("Ljava/lang/String;", "Z"), "Z"),
            Opcode.INVOKE_INTERFACE)).readsKevaBoolean(1, REMEMBERED_KEY))
        assertFalse("not a call", listOf(key(1), default(2)).readsKevaBoolean(1, REMEMBERED_KEY))
    }
}
