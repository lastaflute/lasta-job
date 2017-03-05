/*
 * Copyright 2015-2017 the original author or authors.
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

import org.dbflute.optional.OptionalThing;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/05 Sunday)
 */
public class LaJobNote {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String title; // null allowed
    protected final String desc; // null allowed

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    protected LaJobNote(String title, String desc) {
        // null allowed
        //if (title == null) {
        //    throw new IllegalArgumentException("The argument 'title' should not be null.");
        //}
        //if (description == null) {
        //    throw new IllegalArgumentException("The argument 'desc' should not be null.");
        //}
        this.title = title;
        this.desc = desc;
    }

    public static LaJobNote of(String title, String desc) {
        return new LaJobNote(title, desc);
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public int hashCode() {
        return (title + desc).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LaJobNote)) {
            return false;
        }
        final LaJobNote you = ((LaJobNote) obj);
        return doEquals(title, you.title) && doEquals(desc, you.desc);
    }

    protected boolean doEquals(String me, String you) {
        if (me != null) {
            return me.equals(you);
        } else {
            return you == null;
        }
    }

    @Override
    public String toString() {
        return value();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public String value() {
        final StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (desc != null) {
            sb.append(title != null ? ": " : "").append(desc);
        }
        return sb.toString();
    }

    public OptionalThing<String> getTitle() {
        return OptionalThing.ofNullable(title, () -> {
            throw new IllegalStateException("Not found the title in job note: description=" + desc);
        });
    }

    public OptionalThing<String> getDesc() {
        return OptionalThing.ofNullable(desc, () -> {
            throw new IllegalStateException("Not found the description in job note: title=" + title);
        });
    }
}
