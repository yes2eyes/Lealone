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
package org.lealone.sql.query;

import org.lealone.db.result.LocalResult;
import org.lealone.db.result.ResultTarget;
import org.lealone.db.session.ServerSession;

// 由子类实现具体的查询操作
abstract class QOperator {

    protected final Select select;
    protected final ServerSession session;

    int columnCount;
    ResultTarget target;
    ResultTarget result;
    LocalResult localResult;
    int maxRows; // 实际返回的最大行数
    long limitRows; // 有可能超过maxRows
    int sampleSize;
    int rowCount; // 满足条件的记录数
    int loopCount; // 循环次数，有可能大于rowCount
    boolean loopEnd;

    YieldableSelect yieldableSelect;

    QOperator(Select select) {
        this.select = select;
        session = select.getSession();
    }

    boolean yieldIfNeeded(int rowNumber) {
        return yieldableSelect.yieldIfNeeded(rowNumber);
    }

    boolean canBreakLoop() {
        // 不需要排序时，如果超过行数限制了可以退出循环
        if ((select.sort == null || select.sortUsingIndex) && limitRows > 0 && rowCount >= limitRows) {
            return true;
        }
        // 超过采样数也可以退出循环
        if (sampleSize > 0 && rowCount >= sampleSize) {
            return true;
        }
        return false;
    }

    void start() {
        limitRows = maxRows;
        // 并不会按offset先跳过前面的行数，而是limitRows加上offset，读够limitRows+offset行，然后再从result中跳
        // 因为可能需要排序，offset是相对于最后的结果来说的，而不是排序前的结果
        // limitRows must be long, otherwise we get an int overflow
        // if limitRows is at or near Integer.MAX_VALUE
        // limitRows is never 0 here
        if (limitRows > 0 && select.offsetExpr != null) {
            int offset = select.offsetExpr.getValue(session).getInt();
            if (offset > 0) {
                limitRows += offset;
            }
            if (limitRows < 0) {
                // Overflow
                limitRows = Long.MAX_VALUE;
            }
        }
        rowCount = 0;
        select.setCurrentRowNumber(0);
        sampleSize = select.getSampleSizeValue(session);
    }

    void run() {
    }

    void stop() {
        if (select.offsetExpr != null) {
            localResult.setOffset(select.offsetExpr.getValue(session).getInt());
        }
        if (maxRows >= 0) {
            localResult.setLimit(maxRows);
        }
        if (localResult != null) {
            localResult.done();
            if (target != null) {
                while (localResult.next()) {
                    target.addRow(localResult.currentRow());
                }
                localResult.close();
            }
        }
    }
}
