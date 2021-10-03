/*
 * Copyright 2015-2021 the original author or authors.
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

import java.util.List;

import org.dbflute.utflute.core.PlainTestCase;
import org.dbflute.utflute.core.cannonball.CannonballCar;
import org.dbflute.utflute.core.cannonball.CannonballOption;
import org.dbflute.utflute.core.cannonball.CannonballRun;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.log.SavedHistoryCache;
import org.lastaflute.job.mock.MockJobHistory;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public class SavedHistoryCacheTest extends PlainTestCase {

    public void test_theradSafe() {
        // ## Arrange ##
        SavedHistoryCache cache = new SavedHistoryCache();
        int limit = 3;

        // ## Act ##
        cannonball(new CannonballRun() {
            public void drive(CannonballCar car) {
                LaJobHistory jobHistory = new MockJobHistory();
                int entryNumber = car.getEntryNumber();
                String historyKey = "sea_" + entryNumber;
                if (entryNumber % 2 == 0) {
                    car.teaBreak(10L);
                }
                cache.record(historyKey, jobHistory, limit);
                if (entryNumber % 2 == 1) {
                    car.teaBreak(10L);
                }
                log(historyKey, cache.find(historyKey));
            }
        }, new CannonballOption().threadCount(12));

        // ## Assert ##
        List<LaJobHistory> historyList = cache.list();
        for (LaJobHistory history : historyList) {
            log(history);
        }
        assertEquals(limit, historyList.size());
    }
}
