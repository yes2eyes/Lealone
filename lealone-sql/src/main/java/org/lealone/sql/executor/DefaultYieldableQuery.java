/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.sql.executor;

import org.lealone.db.async.AsyncHandler;
import org.lealone.db.async.AsyncResult;
import org.lealone.db.result.Result;
import org.lealone.db.session.SessionStatus;
import org.lealone.sql.StatementBase;
import org.lealone.sql.router.SQLRouter;

public class DefaultYieldableQuery extends YieldableQueryBase {

    private Boolean completed;

    public DefaultYieldableQuery(StatementBase statement, int maxRows, boolean scrollable,
            AsyncHandler<AsyncResult<Result>> asyncHandler) {
        super(statement, maxRows, scrollable, asyncHandler);
    }

    @Override
    protected void executeInternal() {
        if (completed == null) {
            completed = false;
            SQLRouter.executeQuery(statement, maxRows, scrollable, ar -> handleResult(ar));
        }
    }

    private void handleResult(AsyncResult<Result> ar) {
        try {
            if (ar.isSucceeded()) {
                Result result = ar.getResult();
                setResult(result, result.getRowCount());
                session.setStatus(SessionStatus.STATEMENT_COMPLETED);
            } else {
                setPendingException(ar.getCause());
            }
        } finally {
            completed = true;
        }
    }
}
