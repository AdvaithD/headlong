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
package com.esaulpaugh.headlong.rlp;

import com.esaulpaugh.headlong.TestUtils;
import com.esaulpaugh.headlong.rlp.exception.DecodeException;
import com.esaulpaugh.headlong.rlp.exception.UnrecoverableDecodeException;
import com.esaulpaugh.headlong.util.FastHex;
import com.esaulpaugh.headlong.util.Strings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.esaulpaugh.headlong.util.Strings.HEX;
import static com.esaulpaugh.headlong.util.Strings.UTF_8;
import static com.esaulpaugh.headlong.rlp.RLPDecoder.RLP_STRICT;
import static org.junit.jupiter.api.Assertions.*;

public class RLPStreamTest {

    private static final byte TEST_BYTE = 0x79;
    private static final byte[] TEST_BYTES = "'wort'X3".getBytes(StandardCharsets.UTF_8);
    private static final String TEST_STRING = "2401";

    @Test
    public void testStreamEasy() throws IOException, DecodeException {
        byte[] rlpEncoded = new byte[] {
                (byte) 0xca, (byte) 0xc9, (byte) 0x80, 0x00, (byte) 0x81, (byte) 0xFF, (byte) 0x81, (byte) 0x90, (byte) 0x81, (byte) 0xb6, (byte) '\u230A',
                (byte) 0xb8, 56, 0x09,(byte)0x80,-1,0,0,0,0,0,0,0,36,74,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, -3, -2, 0, 0,
                (byte) 0xf8, 0x38, 0,0,0,0,0,0,0,0,0,0,36,74,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 36, 74, 0, 0,
                (byte) 0x84, 'c', 'a', 't', 's',
                (byte) 0x84, 'd', 'o', 'g', 's',
                (byte) 0xca, (byte) 0x84, 92, '\r', '\n', '\f', (byte) 0x84, '\u0009', 'o', 'g', 's',
        };
        List<RLPItem> collected = RLP_STRICT.collectAll(rlpEncoded);
        try (RLPStream stream = new RLPStream(new ByteArrayInputStream(rlpEncoded))) {
            List<RLPItem> streamed = new ArrayList<>();
            for (RLPItem rlpItem : stream) {
                streamed.add(rlpItem);
            }
            assertTrue(Arrays.deepEquals(collected.toArray(RLPItem.EMPTY_ARRAY), streamed.toArray(RLPItem.EMPTY_ARRAY)));
        }
    }

    @Test
    public void testStreamHard() throws Throwable {
        ReceiveStreamThread thread = new ReceiveStreamThread();
        thread.start();
        thread.join();
        Throwable t = thread.throwable;
        if(t != null) {
            throw t;
        }
    }

    @Test
    public void testUnrecoverable() throws Throwable {
        try (PipedOutputStream pos = new PipedOutputStream();
             PipedInputStream pis = new PipedInputStream(pos, 512);
             RLPStream stream = new RLPStream(pis)) {
            pos.write(0x81);
            pos.write(0x00);
            Iterator<RLPItem> iter = stream.iterator();
            TestUtils.assertThrown(NoSuchElementException.class, "invalid rlp for single byte @ 0", iter::hasNext);
            try (RLPStream stream2 = new RLPStream(pis)) {
                pos.write(0xf8);
                pos.write(0x37);
                Iterator<RLPItem> iter2 = stream2.iterator();
                for (int i = 0; i < 3; i++) {
                    TestUtils.assertThrown(
                            NoSuchElementException.class,
                            "long element data length must be 56 or greater; found: 55 for element @ 0",
                            iter2::hasNext
                    );
                }
            }
        }
    }

    @Test
    public void testInterfaces() {
        try (RLPStream stream = new RLPStream(new ByteArrayInputStream(new byte[0]))) {
            for(RLPItem item : stream) {
                System.out.println(item);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ReceiveStreamThread extends Thread {

        private final Object receiver = new Object();
        private final Object sender;

        private final long zero;
        private final PipedOutputStream pos;
        private final AtomicBoolean canReceive;

        private int readNum = -1;
        private final SendStreamThread senderThread;

        private Throwable throwable;

        public ReceiveStreamThread() {
            this.zero = System.nanoTime();
            this.pos = new PipedOutputStream();
            this.canReceive = new AtomicBoolean(false);
            this.senderThread = new SendStreamThread(zero, pos, receiver, canReceive);
            this.sender = senderThread.sender;
        }

        @Override
        public void run() {
            try (RLPStream stream = new RLPStream(new PipedInputStream(pos, 512))) {

                Iterator<RLPItem> iter = stream.iterator();

                senderThread.setPriority(Thread.MAX_PRIORITY);
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

                senderThread.start();
                waitForNotifiedSender();

                assertNoNext(iter);

                waitForNotifiedSender();

                assertReadSuccess(iter);
                assertArrayEquals(new byte[] { TEST_BYTE }, iter.next().asBytes());
                assertNoNext(iter);

                waitForNotifiedSender();

                for (byte b : TEST_BYTES) {
                    assertReadSuccess(iter);
//                    timestamp(zero),
                    assertArrayEquals(new byte[] { b }, iter.next().asBytes());
                }
                assertNoNext(iter);

                waitForNotifiedSender();

                assertNoNext(iter);

                waitForNotifiedSender();

                assertNoNext(iter);

                waitForNotifiedSender();

                assertReadSuccess(iter);
                assertTrue(iter.hasNext());
                assertEquals(TEST_STRING, iter.next().asString(UTF_8));
                assertReadSuccess(iter);
                assertTrue(iter.hasNext());
                assertArrayEquals(new byte[] { TEST_BYTE }, iter.next().asBytes());
                TestUtils.assertThrown(NoSuchElementException.class, iter::next);
                assertNoNext(iter);
                assertFalse(iter.hasNext());
                TestUtils.assertThrown(NoSuchElementException.class, iter::next);

                senderThread.join();

            } catch (Throwable io) {
                throwable = io;
            } finally {
                canReceive.set(false);
            }
        }

        private void waitForNotifiedSender() throws InterruptedException {
            notifySender();
            synchronized (receiver) {
                while(!canReceive.get()) {
                    receiver.wait();
                }
            }
        }

        private void notifySender() {
            synchronized (sender) {
                canReceive.set(false);
                sender.notify();
            }
        }

        private void assertNoNext(Iterator<RLPItem> iter) throws IOException, UnrecoverableDecodeException {
            RLPStreamTest.assertNoNext(zero, iter);
        }

        private void assertReadSuccess(Iterator<RLPItem> iter) throws IOException, UnrecoverableDecodeException {
            RLPStreamTest.assertReadSuccess(zero, ++readNum, iter);
        }
    }

    private static class SendStreamThread extends Thread {

        private final Object sender = new Object();

        private final long zero;
        private final OutputStream os;
        private final Object receiver;
        private final AtomicBoolean canReceive;

        private SendStreamThread(long zero, OutputStream os, Object receiver, AtomicBoolean canReceive) {
            this.zero = zero;
            this.os = os;
            this.receiver = receiver;
            this.canReceive = canReceive;
        }

        @Override
        public void run() {
            try {
                waitForReceiver();
                write(TEST_BYTE);
                waitForReceiver();
                for (byte b : TEST_BYTES) {
                    write(b);
                }
                waitForReceiver();
                byte[] rlpString = RLPEncoder.encode(Strings.decode(TEST_STRING, UTF_8));
                int i = 0;
                write(rlpString[i++]);
                waitForReceiver();
                write(rlpString[i++]);
                waitForReceiver();
                while(i < rlpString.length) {
                    write(rlpString[i++]);
                }
                write(TEST_BYTE);
                notifyReceiver();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }

        private void waitForReceiver() throws InterruptedException {
            notifyReceiver();
            synchronized (sender) {
                while(canReceive.get()) {
                    sender.wait();
                }
            }
        }

        private void notifyReceiver() {
            synchronized (receiver) {
                canReceive.set(true);
                receiver.notify();
            }
        }

        private void write(byte b) throws IOException {
            os.write(b);
            logWrite(zero, "'" + (char) b + "' (0x" + FastHex.encodeToString(b) +")");
        }
    }

    private static void assertReadSuccess(long zero, int readNum, Iterator<RLPItem> iter) throws IOException, UnrecoverableDecodeException {
//        "no next() found, " + timestamp(zero)
        assertTrue(iter.hasNext());
        logRead(zero, readNum, true);
    }

    private static void assertNoNext(long zero, Iterator<RLPItem> iter) throws IOException, UnrecoverableDecodeException {
        if(iter.hasNext()) {
            throw new AssertionError("unexpected next(): " + iter.next().asString(HEX) + ", " + timestamp(zero));
        }
        logRead(zero, -1, false);
    }

    private static void logWrite(long zero, String message) {
        System.out.println(timestamp(zero) + "\u0009write " + message);
    }

    private static void logRead(long zero, int readNum, boolean success) {
        System.out.println(timestamp(zero) + "\u0009read " + (success ? "success, #" + readNum : "failure"));
    }

    private static String timestamp(long zero) {
        double t = (System.nanoTime() - zero) / 1000000.0;
        String tString = String.valueOf(t);
        StringBuilder sb = new StringBuilder("t=");
        sb.append(tString);
        int n = 10 - tString.length();
        for (int i = 0; i < n; i++) {
            sb.append('0');
        }
        return sb.toString();
    }
}
