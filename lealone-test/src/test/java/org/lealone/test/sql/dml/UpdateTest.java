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
package org.lealone.test.sql.dml;

import java.sql.Connection;
import java.sql.Statement;

import org.junit.Test;
import org.lealone.common.util.JdbcUtils;
import org.lealone.db.api.ErrorCode;
import org.lealone.test.sql.SqlTestBase;

public class UpdateTest extends SqlTestBase {
    @Test
    public void run() {
        createTable("UpdateTest");
        testInsert();
        testUpdate();
        testUpdatePrimaryKey();
        testUpdateIndex();
        testRowLock();
        testColumnLock();
    }

    void testUpdatePrimaryKey() {
        executeUpdate("DROP TABLE IF EXISTS testUpdatePrimaryKey");
        executeUpdate("CREATE TABLE testUpdatePrimaryKey (pk int PRIMARY KEY, f1 int)");
        executeUpdate("INSERT INTO testUpdatePrimaryKey(pk, f1) VALUES(1, 10)");
        executeUpdate("INSERT INTO testUpdatePrimaryKey(pk, f1) VALUES(2, 20)");
        sql = "UPDATE testUpdatePrimaryKey SET pk=3 WHERE pk = 1";
        assertEquals(1, executeUpdate(sql));
        sql = "UPDATE testUpdatePrimaryKey SET pk=2 WHERE pk = 2";
        assertEquals(1, executeUpdate(sql));
    }

    void testUpdateIndex() {
        executeUpdate("DROP TABLE IF EXISTS testUpdateIndex");
        executeUpdate("CREATE TABLE testUpdateIndex (pk int PRIMARY KEY, f1 int, f2 int)");
        executeUpdate("CREATE INDEX i_f2 ON testUpdateIndex(f2)");
        executeUpdate("INSERT INTO testUpdateIndex(pk, f1, f2) VALUES(1, 10, 100)");
        executeUpdate("INSERT INTO testUpdateIndex(pk, f1, f2) VALUES(2, 20, 200)");
        sql = "UPDATE testUpdateIndex SET f1=11 WHERE pk = 1";
        assertEquals(1, executeUpdate(sql));
        sql = "UPDATE testUpdateIndex SET f2=201 WHERE pk = 2";
        assertEquals(1, executeUpdate(sql));
        sql = "SELECT f2 FROM testUpdateIndex WHERE pk = 2";
        try {
            assertEquals(201, getIntValue(1, true));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 测试执行SQL的调度器跟执行页面操作的调度器之间的协作
        for (int i = 100; i < 300; i++) {
            executeUpdate("INSERT INTO testUpdateIndex(pk, f1, f2) VALUES(" + i + ", 10, 100)");
        }
        sql = "SELECT count(*) FROM testUpdateIndex WHERE pk >= 100";
        try {
            assertEquals(200, getIntValue(1, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void testInsert() {
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('01', 'a1', 'b', 51)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('02', 'a1', 'b', 61)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('03', 'a1', 'b', 61)");

        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('25', 'a2', 'b', 51)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('26', 'a2', 'b', 61)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('27', 'a2', 'b', 61)");

        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('50', 'a1', 'b', 12)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('51', 'a2', 'b', 12)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('52', 'a1', 'b', 12)");

        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('75', 'a1', 'b', 12)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('76', 'a2', 'b', 12)");
        executeUpdate("INSERT INTO UpdateTest(pk, f1, f2, f3) VALUES('77', 'a1', 'b', 12)");
    }

    void testUpdate() {
        sql = "UPDATE UpdateTest SET f1 = 'a1', f3 = 61+LENGTH(f2) WHERE pk = '01'";
        assertEquals(1, executeUpdate(sql));

        try {
            sql = "SELECT f1, f2, f3 FROM UpdateTest WHERE pk = '01'";
            assertEquals("a1", getStringValue(1));
            assertEquals("b", getStringValue(2));
            assertEquals(62, getIntValue(3, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void testRowLock() {
        try {
            conn.setAutoCommit(false);
            sql = "UPDATE UpdateTest SET f1 = 'a2' WHERE pk = '02'";
            executeUpdate(sql);

            Connection conn2 = null;
            try {
                conn2 = getConnection();
                conn2.setAutoCommit(false);
                Statement stmt2 = conn2.createStatement();
                stmt2.executeUpdate("SET LOCK_TIMEOUT = 300");
                stmt2.executeUpdate("UPDATE UpdateTest SET f1 = 'a3' WHERE pk = '02'");
                fail();
            } catch (Exception e) {
                assertLockTimeout(conn2, e);
            }

            conn.commit();
            conn.setAutoCommit(true);

            sql = "SELECT f1, f2, f3 FROM UpdateTest WHERE pk = '02'";
            assertEquals("a2", getStringValue(1, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void testColumnLock() {
        try {
            conn.setAutoCommit(false);
            sql = "UPDATE UpdateTest SET f1 = 'a2' WHERE pk = '02'";
            assertEquals(1, executeUpdate(sql));

            // 因为支持列锁，所以两个事务更新同一行的不同字段不会产生冲突
            try {
                Connection conn2 = getConnection();
                conn2.setAutoCommit(false);
                Statement stmt2 = conn2.createStatement();
                String sql2 = "UPDATE UpdateTest SET f2 = 'c' WHERE pk = '02'";
                stmt2.executeUpdate(sql2);
                conn2.commit();
                stmt2.close();
                conn2.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 第三个事务不能进行，因为第一个事务锁住f1字段了
            Connection conn3 = null;
            try {
                conn3 = getConnection();
                conn3.setAutoCommit(false);
                Statement stmt3 = conn3.createStatement();
                stmt3.executeUpdate("SET LOCK_TIMEOUT = 300");
                String sql3 = "UPDATE UpdateTest SET f1 = 'a3' WHERE pk = '02'";
                stmt3.executeUpdate(sql3);
                fail();
            } catch (Exception e) {
                assertLockTimeout(conn3, e);
            }

            Connection conn4 = getConnection();
            conn4.setAutoCommit(false);
            Statement stmt4 = conn4.createStatement();
            String sql4 = "UPDATE UpdateTest SET f3 = 4 WHERE pk = '02'";
            stmt4.executeUpdate(sql4);

            // 可以不按顺序提交事务

            conn4.commit();
            stmt4.close();
            conn4.close();

            conn.commit();
            conn.setAutoCommit(true);

            sql = "SELECT f1, f2, f3 FROM UpdateTest WHERE pk = '02'";
            assertEquals("a2", getStringValue(1));
            assertEquals("c", getStringValue(2));
            assertEquals(4, getIntValue(3, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void assertLockTimeout(Connection conn, Exception e) {
        assertErrorCode(e, ErrorCode.LOCK_TIMEOUT_1);
        if (conn != null)
            JdbcUtils.closeSilently(conn);
        System.out.println(e.getMessage());
    }
}
