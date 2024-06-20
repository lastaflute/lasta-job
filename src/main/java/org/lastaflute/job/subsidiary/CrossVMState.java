/*
 * Copyright 2015-2024 the original author or authors.
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
package org.lastaflute.job.subsidiary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dbflute.optional.OptionalThing;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public class CrossVMState {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected boolean quit;
    protected final Map<String, Object> attributeMap = new ConcurrentHashMap<String, Object>(); // just in case

    // ===================================================================================
    //                                                                            Settings
    //                                                                            ========
    public CrossVMState asQuit() {
        this.quit = true;
        return this;
    }

    public CrossVMState withAttribute(String key, Object value) {
        attributeMap.put(key, value);
        return this;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public boolean isQuit() {
        return quit;
    }

    public <ATTRIBUTE> OptionalThing<ATTRIBUTE> getAttribute(String key, Class<ATTRIBUTE> attrType) {
        final Object obj = attributeMap.get(key);
        return OptionalThing.ofNullable(obj != null ? attrType.cast(obj) : null, () -> {
            throw new IllegalStateException("Not found the attribute by the key: " + key + ", " + attrType);
        });
    }
}
