/*
   Copyright 2019 Evan Saulpaugh

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.esaulpaugh.headlong.abi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

public class MonteCarloTest {

    private static final int N = 50_000;

    private static long[] generateSeeds(long masterSeed, int n) {
        Random r = new Random(masterSeed);
        long[] seeds = new long[n];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = r.nextLong();
        }
        return seeds;
    }

    @Ignore
    @Test
    public void printNewTestCases() throws ParseException {
        final Gson ugly = new GsonBuilder().create();
        final JsonPrimitive version = new JsonPrimitive("1.4.4+commit.3ad2258");
        JsonArray array = new JsonArray();
        int i = 0;
        for(final long seed : generateSeeds(getSeed(System.nanoTime()), 250)) {
            final MonteCarloTestCase.Params params = new MonteCarloTestCase.Params(seed);
            MonteCarloTestCase testCase = new MonteCarloTestCase(params);
            array.add(testCase.toJsonElement(ugly, "headlong_" + i++, version));
        }
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(array));
    }

    @Test
    public void monteCarloThreaded() throws InterruptedException {

        long masterMasterSeed = getSeed(System.nanoTime()); // (long) (Math.sqrt(2.0) * Math.pow(10, 15));

        Thread[] threads = new Thread[Runtime.getRuntime().availableProcessors() - 1];
        int i = 0;
        while (i < threads.length) {
            (threads[i] = newThread(masterMasterSeed + i++, N)).start();
        }
        newThread(masterMasterSeed + i++, N).run();

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println((N * i) + " done, MASTER_MASTER_SEED = " + masterMasterSeed);
    }

    private static Thread newThread(long seed, int n) {
        return new Thread(() -> {
            try {
                doMonteCarlo(seed, n);
            } catch (ParseException pe) {
                throw new RuntimeException(pe);
            }
        });
    }

    private static void doMonteCarlo(long masterSeed, int n) throws ParseException {

        final long[] seeds = generateSeeds(masterSeed, n);

        StringBuilder log = new StringBuilder();

        int i = 0;
        String temp = null;
        MonteCarloTestCase testCase;
        for(final long seed : seeds) {
            final MonteCarloTestCase.Params params = new MonteCarloTestCase.Params(seed);
            try {
                testCase = new MonteCarloTestCase(params);
                temp = testCase.function.getCanonicalSignature();
                testCase.run();
                temp = null;
//                log.append('#')
//                        .append(i)
//                        .append(" PASSED: ")
//                        .append(params.toString())
//                        .append("\t\t")
//                        .append(testCase.function.getCanonicalSignature().substring(testCase.function.getCanonicalSignature().indexOf('('))) // print function params
//                        .append('\n');
                i++;
            } catch (Throwable t) {
                System.out.println(log.toString());
                sleep();
                System.err.println("#" + i + " failed for " + params.toString() + "\t\t" + temp);
                System.err.println("MASTER_SEED = " + masterSeed);
                throw t;
            }
        }

        System.out.println(log.toString());
        System.out.println("MASTER_SEED = " + masterSeed);
    }

    private static class MonteCarloTask extends RecursiveAction {

        private static final long serialVersionUID = -4228469691073264266L;

        private static final int THRESHOLD = 50_000;

        final MonteCarloTestCase testCase;
        private final int start;
        private final int end;

        MonteCarloTask(final MonteCarloTestCase testCase, int start, int end) {
            this.testCase = testCase;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
//            System.out.println("compute(" + start + ", " + end + ")");
            final int start = this.start;
            final int end = this.end;
            final int n = end - start;
            if(n < THRESHOLD) {
                for (int j = 0; j < n; j++) {
                    this.testCase.run();
                }
            } else {
                final int midpoint = start + (n / 2);
                invokeAll(
                        new MonteCarloTask(testCase, start, midpoint),
                        new MonteCarloTask(testCase, midpoint, end)
                );
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MonteCarloTask that = (MonteCarloTask) o;
            return start == that.start &&
                    end == that.end &&
                    Objects.equals(testCase, that.testCase);
        }

        @Override
        public int hashCode() {
            return Objects.hash(testCase, start, end);
        }
    }

//    private static final int TIMEOUT_SECONDS = 60;

    @Test
    public void threadedTest() throws ParseException, InterruptedException, IOException, ClassNotFoundException {

//        System.out.println(params);

//        ForkJoinPool pool = ForkJoinPool.commonPool();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        final long time = System.nanoTime();
        long seed = getSeed(time);
        final long origSeed = seed;
        final long seed2 = getSeed(time + 1);
        System.out.println("orig =  " + seed);
        System.out.println("seed2 = " + seed2);
        System.out.println("xor = " + Long.toHexString(seed ^ seed2));

        MonteCarloTestCase temp;
        String sig;
        do {
            seed++;
            temp = new MonteCarloTestCase(new MonteCarloTestCase.Params(seed));
            sig = temp.function.getCanonicalSignature();
        } while (!sig.contains("string")
                || (!sig.contains("fixed") && !sig.contains("decimal"))
                || (!sig.contains("int") && !sig.contains("address"))
                || (!sig.contains("bytes") && !sig.contains("function"))
                || sig.indexOf('[') < 0
                || sig.indexOf(')') == sig.length() - 1
        );
        System.out.println("n = " + (seed - origSeed));

        final MonteCarloTestCase testCase = temp;

        System.out.println(testCase.function.getCanonicalSignature());
        System.out.println(testCase.params);
        final MonteCarloTask task = new MonteCarloTask(testCase, 0, 20_000);

        oos.writeObject(task);

        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        pool.invoke(task);
        pool.shutdown();
        pool.awaitTermination(20, TimeUnit.SECONDS);

        Thread[] threads = new Thread[8];
        final int len = threads.length;
        for (int i = 0; i < len; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 500; j++) {
                    testCase.run();
                }
            });
        }

        final int len2 = len - 1;
        for (int i = 0; i < len2; i++) {
            threads[i].start();
        }
        threads[len2].run();

        for (int i = 0; i < len2; i++) {
            threads[i].join();
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);

        final MonteCarloTask deserialized = (MonteCarloTask) ois.readObject();

        boolean equal = deserialized.equals(task);
        int d_hash = deserialized.hashCode();
        int t_hash = task.hashCode();

        System.out.println("equal = " + equal);
        System.out.println("hashCodes: " + d_hash + " " + t_hash);

        System.out.println(deserialized.testCase.hashCode() + " == " + task.testCase.hashCode());
        System.out.println(deserialized.testCase.argsTuple.hashCode() + " == " + task.testCase.argsTuple.hashCode());


//        System.out.println(deserialized.testCase.params.hashCode() + " == " + task.testCase.params.hashCode());
//        System.out.println(deserialized.testCase.function.hashCode() + " == " + task.testCase.function.hashCode());
//        System.out.println(deserialized.testCase.function.paramTypes.hashCode() + " == " + task.testCase.function.paramTypes.hashCode());


        if(!equal || d_hash != t_hash) {
            throw new AssertionError("deserialization failure");
        }

        System.out.println("successful deserialization");
    }

    private static void sleep() {
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static long getSeed(final long protoseed) {
        long c = protoseed * (System.nanoTime() << 1) * -System.nanoTime();
        c ^= c << 32;
        return c ^ (c >> 32);
    }

//    public static void main(String[] args0) {
//        byte[] x = new byte[8];
//        for (int i = 1; i <= 20_000; i++) {
//            long seed = getSeed(i);
//            Integers.putLong(seed, x, 0);
//            System.out.println(Strings.encode(x, Strings.BASE64));
//        }
//    }
}
