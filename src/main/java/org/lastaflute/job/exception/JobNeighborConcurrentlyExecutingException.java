/*
 * Copyright 2015-2018 the original author or authors.
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
package org.lastaflute.job.exception;

import org.lastaflute.job.exception.base.LaJobOperationException;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/24 Friday)
 */
public class JobNeighborConcurrentlyExecutingException extends LaJobOperationException {

    private static final long serialVersionUID = 1L;

    public JobNeighborConcurrentlyExecutingException(String msg) {
        super(msg);
    }

    public JobNeighborConcurrentlyExecutingException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
