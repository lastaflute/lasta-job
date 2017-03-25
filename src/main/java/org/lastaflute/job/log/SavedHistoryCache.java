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
package org.lastaflute.job.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dbflute.helper.message.ExceptionMessageBuilder;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.exception.JobHistoryNotFoundException;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public class SavedHistoryCache {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // basically synchronized but just in case
    protected final Map<String, LaJobHistory> historyMap = new ConcurrentHashMap<String, LaJobHistory>();
    protected final List<String> historyKeyList = new CopyOnWriteArrayList<String>();

    // ===================================================================================
    //                                                                           Operation
    //                                                                           =========
    // -----------------------------------------------------
    //                                                 Find
    //                                                ------
    public synchronized OptionalThing<LaJobHistory> find(String historyKey) {
        final LaJobHistory found = historyMap.get(historyKey);
        return OptionalThing.ofNullable(found, () -> {
            final ExceptionMessageBuilder br = new ExceptionMessageBuilder();
            br.addNotice("Not found the job history by the job thread.");
            br.addItem("History Key");
            br.addElement(historyKey);
            br.addItem("Existing Key");
            if (!historyMap.isEmpty()) {
                historyMap.forEach((key, value) -> {
                    br.addElement(key + " = " + value);
                });
            } else {
                br.addElement("*No history");
            }
            final String msg = br.buildExceptionMessage();
            throw new JobHistoryNotFoundException(msg);
        });
    }

    // -----------------------------------------------------
    //                                                Record
    //                                                ------
    public synchronized void record(String historyKey, LaJobHistory jobHistory, int limit) {
        if (historyMap.size() >= limit) {
            final String removedKey = historyKeyList.remove(0);
            historyMap.remove(removedKey);
        }
        historyMap.put(historyKey, jobHistory);
        historyKeyList.add(historyKey);
    }

    // -----------------------------------------------------
    //                                                 List
    //                                                ------
    public synchronized List<LaJobHistory> list() {
        return new ArrayList<LaJobHistory>(historyMap.values());
    }
}
