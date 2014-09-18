/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.airavata.common.utils;

import org.apache.airavata.common.exception.ApplicationSettingsException;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AiravataUtils {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AiravataUtils.class);

    private static Map<String, Integer> jobCountMap;

    private static final String statPath = "/" + Constants.STAT;


    public static final String EXECUTION_MODE="application.execution.mode";
	public static void setExecutionMode(ExecutionMode mode){
		System.setProperty(EXECUTION_MODE, mode.name());
	}
	
	public static ExecutionMode getExecutionMode(){
		if (System.getProperties().containsKey(EXECUTION_MODE)) {
			return ExecutionMode.valueOf(System.getProperty(EXECUTION_MODE));
		}else{
			return ExecutionMode.CLIENT;
		}
	}
	
	public static boolean isServer(){
		return getExecutionMode()==ExecutionMode.SERVER;
	}
	
	public static boolean isClient(){
		return getExecutionMode()==ExecutionMode.CLIENT;
	}
	
	public static void setExecutionAsServer(){
		setExecutionMode(ExecutionMode.SERVER);
	}
	
	public static void setExecutionAsClient(){
		setExecutionMode(ExecutionMode.CLIENT);
	}

    public static Timestamp getCurrentTimestamp() {
        Calendar calender = Calendar.getInstance();
        java.util.Date d = calender.getTime();
        return new Timestamp(d.getTime());
    }

    public static Timestamp getTime(long time) {
        if (time == 0 || time < 0){
            return getCurrentTimestamp();
        }
        return new Timestamp(time);
    }

    public static Map<String, Integer> getJobCountMap(ZooKeeper zk) {
        if (jobCountMap == null) {
            try {
                if (zk.exists(statPath, false) == null) {
                    zk.create(statPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
                byte[] byteData = zk.getData(statPath, new StatReader(zk), null);
                jobCountMap = new HashMap<String, Integer>(); //initialize jobCountMap after register a watcher for stat znode
                if (byteData != null) {
                    String data = new String(byteData);
                    populateJobCountMap(data, zk);
                } else {
                    // nothing to populate , so ignore it
                }
                log.info("Successfully populated job count map by reading zookeeper stat znode");
            } catch (Exception e) {
                log.error("Error while populating job count map by reading stat znode");
            }
        }
        return jobCountMap;
    }

    /**
     * This inner class is being used to populate the job count map.
     */
    private static class StatReader implements Watcher {
        private ZooKeeper zk;

        private StatReader(ZooKeeper zk) throws KeeperException, InterruptedException, ApplicationSettingsException, IOException {
            this.zk = zk;
        }

        @Override
        public void process(WatchedEvent event) {
            try {
                if (event.getType() == Event.EventType.NodeDataChanged) {
                    byte[] statData = zk.getData(statPath, this, null);
                    if (statData != null) {
                        populateJobCountMap(new String(statData) , zk);
                    } else {
                        // all data has been cleaned in stat znode so nothing to do
                    }
                } else if (event.getType() == Event.EventType.NodeChildrenChanged
                        || event.getType() == Event.EventType.None) {
                    zk.getData(statPath, this, null);
                } else {
                    // we don't handle node delete or node create states
                }
            } catch (KeeperException e) {
                log.error("Error while getting data from " + event.getPath(), e);
            } catch (InterruptedException e) {
                log.error("Error while getting data from " + event.getPath(), e);
            } catch (ApplicationSettingsException e) {
                log.error("Error while retrieving zookeeper connection string", e);
            } catch (IOException e) {
                log.error("Error while connecting to ZooKeeper server", e);
            }
        }


    }

    private static void populateJobCountMap(String dataString , ZooKeeper zk) throws KeeperException, InterruptedException, ApplicationSettingsException, IOException {
        if (dataString != null) {
            String[] paths = dataString.split(":");
            String dataStr;
            for (String path : paths) {
                byte[] data = zk.getData(path, null, null);
                if (data == null) {
                    log.error("No job count set for path " + path + ", it returned null for the data");
                } else {
                    dataStr = new String(data);
                    log.info("Update Path : " + path + " - " + dataStr);
                    jobCountMap.put(path, Integer.parseInt(dataStr));
                }
            }
        }
    }
}
