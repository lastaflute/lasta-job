package org.lastaflute.job.key;

import org.dbflute.utflute.core.PlainTestCase;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/05 Sunday)
 */
public class LaJobNoteTest extends PlainTestCase {

    public void test_both() {
        // ## Arrange ##
        // ## Act ##
        LaJobNote note = LaJobNote.of("sea", "land");

        // ## Assert ##
        assertEquals("sea", note.getTitle().get());
        assertEquals("land", note.getDesc().get());
        assertTrue(note.equals(LaJobNote.of("sea", "land")));
        assertFalse(note.equals(LaJobNote.of("sea", null)));
        assertFalse(note.equals(LaJobNote.of(null, "land")));
        assertFalse(note.equals(LaJobNote.of(null, null)));
    }

    public void test_titleOnly() {
        // ## Arrange ##
        // ## Act ##
        LaJobNote note = LaJobNote.of("sea", null);

        // ## Assert ##
        assertEquals("sea", note.getTitle().get());
        assertFalse(note.getDesc().isPresent());
        assertFalse(note.equals(LaJobNote.of("sea", "land")));
        assertTrue(note.equals(LaJobNote.of("sea", null)));
        assertFalse(note.equals(LaJobNote.of(null, "land")));
        assertFalse(note.equals(LaJobNote.of(null, null)));
    }

    public void test_descOnly() {
        // ## Arrange ##
        // ## Act ##
        LaJobNote note = LaJobNote.of(null, "land");

        // ## Assert ##
        assertFalse(note.getTitle().isPresent());
        assertEquals("land", note.getDesc().get());
        assertFalse(note.equals(LaJobNote.of("sea", "land")));
        assertFalse(note.equals(LaJobNote.of("sea", null)));
        assertTrue(note.equals(LaJobNote.of(null, "land")));
        assertFalse(note.equals(LaJobNote.of(null, null)));
    }
}
