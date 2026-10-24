package app.osprey.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsResultTest {

    private fun folder(path: String) = FolderSetting("ref:$path", FolderLocation.of("primary", path), path)

    private fun rejection(reason: SettingsRejection) =
        SettingsResult.Rejected(reason, candidate = folder("Chosen"), conflictsWith = folder("Other"))

    @Test
    fun `an input inside the output names both folders and says to leave the output`() {
        assertEquals(
            "\"Chosen\" is the output folder or inside it (\"Other\"). Choose a folder outside the output.",
            rejection(SettingsRejection.INPUT_INSIDE_OUTPUT).message,
        )
    }

    @Test
    fun `an input containing the output names both folders and says to avoid it`() {
        assertEquals(
            "\"Chosen\" contains the output folder \"Other\". Choose a folder that does not contain it.",
            rejection(SettingsRejection.INPUT_CONTAINS_OUTPUT).message,
        )
    }

    @Test
    fun `an input overlapping another input names the one already listed`() {
        assertEquals(
            "\"Chosen\" overlaps the input folder \"Other\" that is already in the list.",
            rejection(SettingsRejection.INPUT_OVERLAPS_INPUT).message,
        )
    }

    @Test
    fun `an output overlapping an input tells the user to remove the input or pick another output`() {
        assertEquals(
            "The output folder \"Chosen\" overlaps the input folder \"Other\". " +
                "Remove that input first or choose another output.",
            rejection(SettingsRejection.OUTPUT_OVERLAPS_INPUT).message,
        )
    }

    @Test
    fun `every refusal reason has a message naming both folders`() {
        for (reason in SettingsRejection.entries) {
            val message = rejection(reason).message

            assertTrue("$reason: $message", message.contains("Chosen") && message.contains("Other"))
        }
    }
}
