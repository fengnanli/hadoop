/**
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

package org.apache.hadoop.hdfs.server.federation.router;

import static org.apache.hadoop.hdfs.server.federation.FederationTestUtils.createMountTableEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.server.federation.MiniRouterDFSCluster.RouterContext;
import org.apache.hadoop.hdfs.server.federation.RouterConfigBuilder;
import org.apache.hadoop.hdfs.server.federation.StateStoreDFSCluster;
import org.apache.hadoop.hdfs.server.federation.resolver.order.DestinationOrder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test suite for Router Web Hdfs methods
 */
public class TestRouterWebHdfsMethods {
  static final Logger LOG =
      LoggerFactory.getLogger(TestRouterWebHdfsMethods.class);

  private static StateStoreDFSCluster cluster;
  private static RouterContext router;
  private static String httpUri;

  @BeforeClass
  public static void globalSetUp() throws Exception {
    cluster = new StateStoreDFSCluster(false, 2);
    Configuration conf = new RouterConfigBuilder()
        .stateStore()
        .rpc()
        .http()
        .admin()
        .build();
    cluster.addRouterOverrides(conf);
    cluster.startCluster();
    cluster.startRouters();
    cluster.waitClusterUp();
    router = cluster.getRandomRouter();
    httpUri = "http://"+router.getHttpAddress();
  }

  @AfterClass
  public static void tearDown() {
    if (cluster != null) {
      cluster.shutdown();
      cluster = null;
    }
  }

  @Test
  public void testWebHdfsCreate() throws Exception {
    // case 1: the file is created at default ns (ns0)
    String path1 = "/tmp/file";
    URL url = new URL(getUri(path1));
    LOG.info(url.toString());
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("PUT");
    assertEquals(HttpURLConnection.HTTP_CREATED, conn.getResponseCode());
    verifyFileExists("ns0", path1);
    verifyFileNotExists("ns1", path1);

    // case 2: the file is created at mounted ns (ns1)
    String mountPoint = "/tmp-ns1";
    String path2 = "/tmp-ns1/file";
    createMountTableEntry(
        router.getRouter(), mountPoint,
        DestinationOrder.RANDOM, Collections.singletonList("ns1"));
    URL url2 = new URL(getUri(path2));
    LOG.info(url2.toString());
    conn = (HttpURLConnection) url2.openConnection();
    conn.setRequestMethod("PUT");
    assertEquals(HttpURLConnection.HTTP_CREATED, conn.getResponseCode());
    verifyFileExists("ns1", path2);
    verifyFileNotExists("ns0", path2);
  }

  private String getUri(String path) {
    final String user = System.getProperty("user.name");
    final StringBuilder uri = new StringBuilder(httpUri);
    uri.append("/webhdfs/v1").
        append(path).
        append("?op=CREATE").
        append("&user.name=" + user);
    return uri.toString();
  }

  private void verifyFileExists(String ns, String path) throws Exception {
    FileSystem fs = cluster.getNamenode(ns, null).getFileSystem();
    try {
      fs.getFileStatus(new Path(path));
    } catch (FileNotFoundException e) {
      assertTrue("File should exist in " + ns, false);
    }
  }

  private void verifyFileNotExists(String ns, String path) throws Exception {
    FileSystem fs = cluster.getNamenode(ns, null).getFileSystem();
    try {
      fs.getFileStatus(new Path(path));
    } catch (FileNotFoundException e) {
      assertTrue("File should not exist in " + ns, true);
    }
  }
}
