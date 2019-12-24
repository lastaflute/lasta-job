/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
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
