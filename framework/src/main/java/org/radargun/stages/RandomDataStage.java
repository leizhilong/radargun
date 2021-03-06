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

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.utils.Utils;

/**
 * <p>
 * Generates random data to fill the cache. The seed used to instantiate the java.util.Random object
 * can be specified, so that the same data is generated between runs. To specify generating a fixed
 * amount of data in the cache, specify the valueSize and valueCount parameters. The number of
 * values will be divided by the return value of <code>getActiveSlaveCount()</code>. To generate
 * data based on the amount of available RAM, specify the valueSize and ramPercentage parameters.
 * The amount of free memory on each node will be calculated and then used to determine the number
 * of values that are written by the node.
 * </p>
 * 
 * <p>
 * To add a precise amount of data to the cache, you need to be aware of the storage overhead. For a
 * byte array, each value needs an additional 152 bytes. When <code>stringData</code> is enabled,
 * the values will require 2 * <code>valueSize</code> bytes + the additional 152 bytes. Keep these
 * values in mind when calculating the <code>valueCount</code>.
 * </p>
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Generates random data to fill the cache.")
public class RandomDataStage extends AbstractDistStage {
   private long nodePutCount;

   @Property(doc = "The seed to use for the java.util.Random object. "
         + "The default is the return value of Calendar.getInstance().getWeekYear().")
   private long randomSeed = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);

   @Property(doc = "The size of the values to put into the cache. The default size is 1MB (1024 * 1024).")
   private int valueSize = 1024 * 1024;

   @Property(doc = "The number of values of valueSize to write to the cache. "
         + "Either valueCount or ramPercentageDataSize should be specified, but not both.")
   private long valueCount = -1;

   @Property(doc = "A double that represents the percentage of the total Java heap "
         + "used to determine the amount of data to put into the cache."
         + "Either valueCount or ramPercentageDataSize should be specified, but not both.")
   private double ramPercentage = -1;

   @Property(doc = "The name of the bucket where keys are written. The default is null")
   private String bucket = null;

   @Property(doc = "If true, then String objects with printable characters are written to the cache."
         + "The default is false")
   private boolean stringData = false;

   @Property(doc = "If true, then the time for each put operation is written to the logs. The default is false")
   private boolean printWriteStatistics = false;

   @Property(doc = "If true, then the random word generator selects a word from a pre-defined list. "
         + "The default is false")
   private boolean limitWordCount = false;

   @Property(doc = "The maximum number of words to generate in the pre-defined list of words used with limitWordCount."
         + "The default is 100.")
   private int maxWordCount = 100;

   @Property(doc = "The maximum number of characters allowed in a word. The default is 20.")
   private int maxWordLength = 20;

   @Property(doc = "If false, then each node in the cluster generates a list of maxWordCount words. "
         + "If true, then each node in the cluster shares the same list of words. The default is false.")
   private boolean shareWords = false;

   private Random random;

   private String[][] words = null;

   /*
    * From http://infinispan.blogspot.com/2013/01/infinispan-memory-overhead.html
    */
   public final static int LIBRARY_MODE_VALUE_BYTE_OVERHEAD = 152;

   Runtime runtime = null;

   private long targetMemoryUse;
   private int newlinePunctuationModulo = 10;

   /**
    * 
    * Fills a multi-dimensional array with randomly generated words. The first dimension of the
    * array is based on the length of the word in characters, and runs from 1 to maxWordLength.
    * Dividing the wordCount by maxWordLength determines how many words of each length are
    * generated.
    * 
    * @param wordCount
    *           the total number of words to generate
    * @param maxWordLength
    *           the maximum size in characters for a word
    */
   private void fillWordArray(int wordCount, int maxWordLength) {
      int wordsPerLength = wordCount / maxWordLength;
      words = new String[maxWordLength][wordsPerLength];
      for (int i = 1; i <= maxWordLength; i++) {
         for (int j = 0; j < wordsPerLength; j++) {
            /*
             * Intern the string to reduce memory usage since these words will be used multiple
             * times
             */
            words[i - 1][j] = new String(generateRandomUniqueWord(i, false)).intern();
         }
      }
      log.trace("Slave" + this.getSlaveIndex() + " words array = " + Arrays.deepToString(words));
   }

   @Override
   public void initOnSlave(SlaveState slaveState) {
      super.initOnSlave(slaveState);

      if (shareWords && limitWordCount) {
         random = new Random(randomSeed);
      } else {
         /*
          * Add the slaveIndex to the seed to guarantee that each node generates a different word
          * list
          */
         random = new Random(randomSeed + slaveIndex);
      }
      fillWordArray(maxWordCount, maxWordLength);
   }

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = newDefaultStageAck();
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();

      if (cacheWrapper == null) {
         result.setError(true);
         result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      if (ramPercentage > 0 && valueCount > 0) {
         result.setError(true);
         result.setErrorMessage("Either valueCount or ramPercentageDataSize should be specified, but not both");
         return result;
      }

      if (ramPercentage > 1) {
         result.setError(true);
         result.setErrorMessage("The percentage of RAM can not be greater than one.");
         return result;
      }

      if (shareWords && !limitWordCount) {
         result.setError(true);
         result.setErrorMessage("The shareWords property can only be true when limitWordCount is also true.");
         return result;
      }

      if (limitWordCount && !stringData) {
         result.setError(true);
         result.setErrorMessage("The limitWordCount property can only be true when stringData is also true.");
         return result;
      }

      runtime = Runtime.getRuntime();
      int valueSizeWithOverhead = LIBRARY_MODE_VALUE_BYTE_OVERHEAD;
      /*
       * String data is twice the size of a byte array
       */
      if (stringData) {
         valueSizeWithOverhead += (valueSize * 2);
      } else {
         valueSizeWithOverhead += valueSize;
      }

      if (ramPercentage > 0) {
         System.gc();
         targetMemoryUse = (long) (runtime.maxMemory() * ramPercentage);
         log.trace("targetMemoryUse: " + Utils.kbString(targetMemoryUse));

         nodePutCount = (long) Math.ceil(targetMemoryUse / valueSizeWithOverhead);
      } else {
         nodePutCount = (long) Math.ceil(valueCount / getActiveSlaveCount());
         /*
          * Add one to the nodeCount on each slave with an index less than the remainder so that the
          * correct number of values are written to the cache
          */
         if ((valueCount % getActiveSlaveCount() != 0) && getSlaveIndex() < (valueCount % getActiveSlaveCount())) {
            nodePutCount++;
         }
      }

      long putCount = nodePutCount;
      long bytesWritten = 0;
      try {
         byte[] buffer = new byte[valueSize];
         while (putCount > 0) {
            String key = Integer.toString(getSlaveIndex()) + "-" + random.nextLong();

            long start;
            if (stringData) {
               if (putCount % 5000 == 0) {
                  log.info(putCount + ": Writing string length " + valueSize + " to cache key: " + key);
               }

               String cacheData = generateRandomStringData(valueSize);
               bytesWritten += (valueSize * 2);
               start = System.nanoTime();
               cacheWrapper.put(bucket, key, cacheData);
            } else {
               if (putCount % 5000 == 0) {
                  log.info(putCount + ": Writing " + valueSize + " bytes to cache key: " + key);
               }

               random.nextBytes(buffer);
               bytesWritten += valueSize;
               start = System.nanoTime();
               cacheWrapper.put(bucket, key, buffer);
            }
            if (printWriteStatistics) {
               log.info("Put on slave" + getSlaveIndex() + " took "
                     + Utils.prettyPrintTime(System.nanoTime() - start, TimeUnit.NANOSECONDS));
            }

            putCount--;
         }
         System.gc();
         log.info("Memory - free: " + Utils.kbString(runtime.freeMemory()) + " - max: "
               + Utils.kbString(runtime.maxMemory()) + "- total: " + Utils.kbString(runtime.totalMemory()));
         result.setPayload(new long[] { nodePutCount, bytesWritten, targetMemoryUse });
      } catch (Exception e) {
         log.fatal("An exception occurred", e);
         result.setError(true);
         result.setErrorMessage("An exception occurred");
      }

      return result;
   }

   private String generateRandomStringData(int dataSize) {
      /*
       * Generate a random string of "words" using random single and multi-byte characters that are
       * separated by punctuation marks and whitespace.
       */
      String punctuationChars = "!,.;?";
      int wordLength = maxWordLength;

      CharBuffer data = CharBuffer.allocate(dataSize);
      while (data.remaining() > 0) {
         String word;
         if (limitWordCount) {
            word = pickRandomWord(wordLength);
         } else {
            word = generateRandomUniqueWord(wordLength, true);
         }
         data = data.put(word);

         if (data.remaining() >= 2 && random.nextInt() % newlinePunctuationModulo == 0) {
            data.put(punctuationChars.charAt(random.nextInt(punctuationChars.length() - 1)));
            data.put('\n');
         } else {
            if (data.remaining() >= 1) {
               data.put(' ');
            }
         }

         if (data.remaining() < wordLength) {
            wordLength = data.remaining();
         }
      }

      return data.toString();
   }

   /**
    * 
    * Randomly selects a random length word based on the words array defined above
    * 
    * @param maxLength
    *           the maximum length of the word
    * @return the word as a String whose length may be less than <code>maxLength</code>
    */
   private String pickRandomWord(int maxLength) {
      String word = "";
      String[] pickWords = {};
      int pick = 0;
      // Random.nextInt(0) generates an error
      if (maxLength - 1 > 0) {
         int wordLength = random.nextInt(maxLength - 1) + 1;
         pickWords = words[wordLength - 1];
         if (pickWords.length - 1 > 0) {
            pick = random.nextInt(pickWords.length - 1);
            word = pickWords[pick];
         }
      }
      return word;
   }

   /**
    * 
    * Generates a random length "word" by randomly selecting single and multi-byte characters
    * 
    * @param maxLength
    *           the maximum length of the word
    * @param randomLength
    *           if <code>true</code>, use a random length with a max value of <code>maxLength</code>
    * @return the word as a String whose length may be less than <code>maxLength</code>
    */
   private String generateRandomUniqueWord(int maxLength, boolean randomLength) {
      String singleByteChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
      String multiByteChars = "ÅÄÇÉÑÖÕÜàäâáãçëèêéîïìíñôöòóüûùúÿ";
      StringBuilder data = new StringBuilder();

      int wordLength = maxLength;
      if (randomLength && maxLength - 1 > 0) {
         wordLength = random.nextInt(maxLength - 1) + 1;
      }

      for (int i = wordLength; i > 0; i--) {
         if (random.nextBoolean()) {
            data.append(multiByteChars.charAt(random.nextInt(multiByteChars.length() - 1)));
         } else {
            data.append(singleByteChars.charAt(random.nextInt(singleByteChars.length() - 1)));
         }
      }

      return data.toString();
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      super.processAckOnMaster(acks, masterState);
      log.info("--------------------");
      if (ramPercentage > 0) {
         if (stringData) {
            log.info("Filled cache with String objects totaling " + (ramPercentage * 100) + "% of the Java heap");
         } else {
            log.info("Filled cache with byte arrays totaling " + (ramPercentage * 100) + "% of the Java heap");
         }
      } else {
         if (stringData) {
            log.info("Filled cache with " + Utils.kbString((valueSize * 2) * valueCount) + " of String objects");
         } else {
            log.info("Filled cache with " + Utils.kbString(valueSize * valueCount) + " of byte arrays");
         }
      }
      long totalValues = 0;
      long totalBytes = 0;
      for (DistStageAck ack : acks) {
         long[] result = (long[]) ((DefaultDistStageAck) ack).getPayload();
         if (result != null) {
            totalValues += result[0];
            totalBytes += result[1];
            String logInfo = "Slave " + ((DefaultDistStageAck) ack).getSlaveIndex() + " wrote " + result[0]
                  + " values to the cache with a total size of " + Utils.kbString(result[1]);
            if (ramPercentage > 0) {
               logInfo += "; targetMemoryUse = " + Utils.kbString(result[2]);
            }
            log.info(logInfo);
         } else {
            log.info("No results retrieved from Slave" + ((DefaultDistStageAck) ack).getSlaveIndex());
         }
      }
      log.info("The cache contains " + totalValues + " values with a total size of " + Utils.kbString(totalBytes));
      if (limitWordCount) {
         int totalWordCount = maxWordCount;
         if (!shareWords) {
            totalWordCount = maxWordCount * getActiveSlaveCount();
         }
         log.info(totalWordCount + " words were generated with a maximum length of " + maxWordLength + " characters");
      }
      log.info("--------------------");
      return true;
   }
}
