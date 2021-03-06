/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.Debugable;
import org.radargun.stages.helpers.Range;
import org.radargun.state.MasterState;
import org.radargun.stressors.BackgroundOpsManager;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage for checking presence or absence of data uploaded by BackgroundStressors")
public class CheckDataStage extends AbstractDistStage {

   @Property(optional = false, doc = "Number of entries with key in form key{number} in the cache.")
   private int numEntries;

   @Property(optional = false, doc = "Number of bytes carried in single entry.")
   private int entrySize;

   @Property(doc = "Entries that have not the form key{number} but occur in the cluster. This string specifies " +
         "a polynomial in number of slaves: 1,2,3 with 4 slaves would result in 1 + 2*4 + 3*4*4 = 57 extra entries." +
         "Defaults to 0.")
   private String extraEntries;

   @Property(doc = "Number of owners of one key (primary + backups). If negative the number is multiplied by cluster " +
         "size, therefore, -1 means full replication. Default is -1.")
   private int numOwners = -1;

   @Property(doc = "Number of thread per node which check data validity. Default is 1.")
   private int checkThreads = 1;

   @Property(doc = "Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries." +
         "This option disables such behaviour. Default is false.")
   private boolean ignoreSum = false;

   @Property(doc = "Hint how many slaves are currently alive - if set to > 0 then the query for amount of entries in" +
         "this cache is postponed until the cache appears to be fully replicated. By default this is disabled.")
   private int liveSlavesHint = -1;

   @Property(doc = "If set to true, we are checking that the data are NOT in the cluster anymore. Default is false.")
   private boolean deleted = false;

   @Property(doc = "Number of queries after which a DEBUG log message is printed. Default is 10000.")
   private int logChecksCount = 10000;

   @Property(doc = "If the GET request results in null response, call wrapper-specific functions to show debug info. " +
         "Default is false.")
   private boolean debugNull = false;

   @Property(doc = "If entry is null, fail immediatelly. Default is false.")
   private boolean failOnNull = false;
    
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaves != null && !slaves.contains(getSlaveIndex())) {
         return ack;
      }
      CheckResult result = new CheckResult();
      try {
         if (slaveState.getCacheWrapper() == null) {         
            // this slave is dead and does not participate on check
            return ack;
         }
         if (checkThreads <= 1) {
            result = checkRange(0, numEntries);
         } else {
            ExecutorService executor = Executors.newFixedThreadPool(checkThreads);
            List<Callable<CheckResult>> tasks = new ArrayList<Callable<CheckResult>>();
            for (int i = 0; i < checkThreads; ++i) {
               Range range = Range.divideRange(numEntries, checkThreads, i);
               tasks.add(new CheckRangeTask(range.getStart(), range.getEnd()));
            }
            for (Future<CheckResult> future : executor.invokeAll(tasks)) {
               CheckResult value = future.get();
               result.merge(value);
            }
         }
      } catch (Exception e) {         
         log.error(e);
         ack.setError(true);
         ack.setRemoteException(e);
         ack.setErrorMessage("Failed to check entries");
         return ack;
      }

      CacheWrapper wrapper = slaveState.getCacheWrapper();
      if (wrapper != null && wrapper.isRunning()) {
         if (!isDeleted()) {
            if (result.found != getExpectedNumEntries()) {
               ack.setError(true);
               ack.setErrorMessage("Found " + result.found + " entries while " + getExpectedNumEntries() + " should be loaded.");
               ack.setPayload(result);
               return ack;
            }
         } else {
            if (result.found > 0) {
               ack.setError(true);
               ack.setErrorMessage("Found " + result.found + " entries while these should be deleted.");
               ack.setPayload(result);
               return ack;
            }
         }
         if (liveSlavesHint > 0) {
            // try to wait until data are properly replicated
            int myExpectedSize;
            int extraEntries = getExtraEntries();
            int commonEntries = isDeleted() ? 0 : numEntries;
            if (numOwners < 0) {
               myExpectedSize = -numOwners * getActiveSlaveCount() * (commonEntries + extraEntries) / liveSlavesHint;
            } else {
               myExpectedSize = numOwners * (commonEntries + extraEntries) / liveSlavesHint;
            }
            for (int attempt = 0; attempt < 5; ++attempt) {
               int local = wrapper.getLocalSize();
               double ratio = (double) local / (double) myExpectedSize;
               if (ratio < 0.9 || ratio > 1.1) {
                  log.warn("Local size (" + wrapper.getLocalSize() + ") differs substantially from expected size (" + myExpectedSize + "), waiting 30s to let it replicate");
                  try {
                     Thread.sleep(30000);
                  } catch (InterruptedException e) {
                     break;
                  }
               } else break;
            }
         }
         ack.setPayload(wrapper.getLocalSize());
      }
      return ack;
   }
   
   private class CheckRangeTask implements Callable<CheckResult> {
      private int from, to;
      
      public CheckRangeTask(int from, int to) {
         this.from = from;
         this.to = to;
      }
      
      @Override
      public CheckResult call() throws Exception {
         try {
            return checkRange(from, to);
         } catch (Exception e) {
            log.error(e);
            return null;
         }
      }
   }
   
   protected int getExpectedNumEntries() {
      return numEntries;
   }
   
   protected CheckResult checkRange(int from, int to) {
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      CheckResult result = new CheckResult();
      BackgroundOpsManager bgStats = (BackgroundOpsManager) slaveState.get(BackgroundOpsManager.NAME);
      String bucketId = BackgroundOpsManager.NAME;
      if (bgStats != null) bucketId = bgStats.getBucketId();
      for (int i = from; i < to; ++i, ++result.checked) {
         if (result.checked % logChecksCount == 0) {
            log.debug("Checked " + result.checked + " entries, so far " + result);
         }
         try {
            Object value = wrapper.get(bucketId, "key" + i);
            if (!isDeleted()) {
               if (value != null && value instanceof byte[] && (entrySize <= 0 || ((byte[]) value).length == entrySize)) {
                  result.found++;
               } else {
                  if (value == null) {
                     result.nullValues++;
                     if (debugNull && wrapper instanceof Debugable) {
                        ((Debugable) wrapper).debugInfo(bucketId);
                        ((Debugable) wrapper).debugKey(bucketId, "key" + i);
                     }
                     if (failOnNull) {
                        result.checked++;
                        break;
                     }
                  } else {
                     result.invalidValues++;
                  }
                  if (log.isTraceEnabled()) {
                     log.trace("Key" + i + " has unexpected value " + value);
                  }
               }
            } else {
               if (value != null) {
                  result.found++;
                  if (log.isTraceEnabled()) {
                     log.trace("Key" + i + " still has value " + value);
                  }
               } else {
                  result.nullValues++;
               }
            }
         } catch (Exception e) {
            result.exceptions++;
            if (log.isTraceEnabled()) {
               log.trace("Error retrieving value for key" + i + "\n" + e);
            }
         }
      }
      return result;
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      boolean success = super.processAckOnMaster(acks, masterState);
      if (success) {
         int sumSize = 0;
         for (DistStageAck ack : acks) {
            DefaultDistStageAck dack = (DefaultDistStageAck) ack;
            if (dack.getPayload() != null) {
               Integer localSize = (Integer) dack.getPayload();
               log.debug("Slave " + dack.getSlaveIndex() + " has local size " + localSize);
               sumSize += localSize;
            }
         }
         if (ignoreSum) {
            log.info("The sum size is " + sumSize);
         } else {
            int expectedSize;
            int extraEntries = getExtraEntries();
            int commonEntries = isDeleted() ? 0 : numEntries;            
            if (numOwners < 0) {
               expectedSize = -numOwners * getActiveSlaveCount() * (commonEntries + extraEntries);
            } else {
               expectedSize = numOwners * (commonEntries + extraEntries);
            }
            if (expectedSize != sumSize) {
               log.error("The cache should contain " + expectedSize + " entries (including backups) but contains " + sumSize + " entries.");
               return false;
            }
         }
      }
      return success;
   }

   public int getNumEntries() {
      return this.numEntries;
   }

   private int getExtraEntries() {
      if (extraEntries == null) return 0;
      
      int sum = 0;
      int multiplicator = 1;      
      try {
         for (String entries : extraEntries.split(",")) {
            int count = Integer.parseInt(entries);
            sum += count * multiplicator;
            multiplicator *= getActiveSlaveCount();
         }
      } catch (NumberFormatException e) {
         log.error("Cannot parse " + extraEntries);
      }
      return sum;
   }

   public boolean isDeleted() {
      return deleted;
   }

   public int getLogChecksCount() {
      return logChecksCount;
   }

   protected static class CheckResult implements Serializable {
      public long checked;
      public long found;
      public long nullValues;
      public long invalidValues;
      public long exceptions;

      public void merge(CheckResult value) {
         if (value == null) return;
         checked += value.checked;
         found += value.found;
         nullValues += value.nullValues;
         invalidValues += value.invalidValues;
         exceptions += value.invalidValues;
      }

      @Override
      public String toString() {
         return String.format("[checked=%d, found=%d, nullValues=%d, invalidValues=%d, exceptions=%d]",
                              checked, found, nullValues, invalidValues, exceptions);
      }
   }
   
}
