package dev.lyricair.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorValueTest {
    @Test void acceptsHexWithOptionalHashAndRejectsIncompleteInput() {
        assertEquals(0xA68CFF, ColorValue.parse(" #a68cff ").orElseThrow());
        assertEquals(0x8CFFBF, ColorValue.parse("8CFFBF").orElseThrow());
        for (String input : new String[]{"", "#", "#FFF", "GGGGGG", "#FFFFFFFF"}) assertTrue(ColorValue.parse(input).isEmpty());
    }
    @Test void editingOneChannelPreservesTheOthers() {
        assertEquals(0x12FF56, ColorValue.channel(0x123456, 8, 999));
        assertEquals(0x123400, ColorValue.channel(0x123456, 0, -1));
        assertEquals("#00AB09", ColorValue.hex(0x00AB09));
    }
}
