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

import com.esaulpaugh.headlong.util.Integers;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static com.esaulpaugh.headlong.rlp.DataType.LIST_LONG_OFFSET;
import static com.esaulpaugh.headlong.rlp.DataType.LIST_SHORT_OFFSET;
import static com.esaulpaugh.headlong.rlp.DataType.MIN_LONG_DATA_LEN;
import static com.esaulpaugh.headlong.rlp.DataType.STRING_LONG_OFFSET;
import static com.esaulpaugh.headlong.rlp.DataType.STRING_SHORT_OFFSET;

/** For encoding data to Recursive Length Prefix format. */
public final class RLPEncoder {
// -------------- MADE VISIBLE TO rlp.eip778 package -------------------------------------------------------------------
    static void insertRecordContentList(int dataLen, long seq, List<KeyValuePair> pairs, ByteBuffer bb) {
        if(seq >= 0) {
            pairs.sort(KeyValuePair.PAIR_COMPARATOR);
            insertListPrefix(dataLen, bb);
            encodeString(seq, bb);
            for (KeyValuePair pair : pairs) {
                encodeKeyValuePair(pair, bb);
            }
        } else {
            throw new IllegalArgumentException("negative seq");
        }
    }

    static void insertRecordSignature(byte[] signature, ByteBuffer bb) {
        encodeItem(signature, bb);
    }

    static int dataLen(List<KeyValuePair> pairs) {
        long sum = 0;
        for (KeyValuePair pair : pairs) {
            sum += stringEncodedLen(pair.getKey()) + stringEncodedLen(pair.getValue());
        }
        return requireNoOverflow(sum);
    }

    static int prefixLength(int dataLen) {
        return isShort(dataLen) ? 1 : 1 + Integers.len(dataLen);
    }

    static void insertListPrefix(int dataLen, ByteBuffer bb) {
        if(isShort(dataLen)) {
            bb.put((byte) (LIST_SHORT_OFFSET + dataLen));
        } else {
            bb.put((byte) (LIST_LONG_OFFSET + Integers.len(dataLen)));
            Integers.putLong(dataLen, bb);
        }
    }
// ---------------------------------------------------------------------------------------------------------------------
    private static int requireNoOverflow(long val) {
        if(val <= Integer.MAX_VALUE) {
            return (int) val;
        }
        throw new IllegalArgumentException("integer overflow");
    }

    private static void encodeKeyValuePair(KeyValuePair pair, ByteBuffer bb) {
        encodeString(pair.getKey(), bb);
        encodeString(pair.getValue(), bb);
    }

    private static boolean isShort(int dataLen) {
        return dataLen < MIN_LONG_DATA_LEN;
    }

    private static int sumEncodedLen(Iterable<?> rawItems) {
        long sum = 0;
        for (Object raw : rawItems) {
            sum += encodedLen(raw);
        }
        return requireNoOverflow(sum);
    }

    private static int encodedLen(Object raw) {
        if (raw instanceof byte[]) {
            return stringEncodedLen((byte[]) raw);
        }
        if (raw instanceof Iterable<?>) {
            return listEncodedLen((Iterable<?>) raw);
        }
        if(raw instanceof Object[]) {
            return listEncodedLen(Arrays.asList((Object[]) raw));
        }
        if(raw == null) {
            throw new NullPointerException();
        }
        throw new IllegalArgumentException("unsupported object type: " + raw.getClass().getName());
    }

    private static int stringEncodedLen(byte[] byteString) {
        final int dataLen = byteString.length;
        return isShort(dataLen)
                ? dataLen == 1 && byteString[0] >= 0x00 // same as (byteString[0] & 0xFF) < 0x80
                    ? 1
                    : 1 + dataLen
                : 1 + Integers.len(dataLen) + dataLen;
    }

    private static int listEncodedLen(Iterable<?> items) {
        final int dataLen = sumEncodedLen(items);
        if(isShort(dataLen)) {
            return 1 + dataLen;
        }
        return 1 + Integers.len(dataLen) + dataLen;
    }

    private static void encodeItem(Object raw, ByteBuffer bb) {
        if (raw instanceof byte[]) {
            encodeString((byte[]) raw, bb);
        } else if (raw instanceof Iterable<?>) {
            Iterable<?> elements = (Iterable<?>) raw;
            encodeList(sumEncodedLen(elements), elements, bb);
        } else if(raw instanceof Object[]) {
            Iterable<Object> elements = Arrays.asList((Object[]) raw);
            encodeList(sumEncodedLen(elements), elements, bb);
        } else if(raw == null) {
            throw new NullPointerException();
        } else {
            throw new IllegalArgumentException("unsupported object type: " + raw.getClass().getName());
        }
    }

    private static void encodeString(long val, ByteBuffer bb) {
        final int dataLen = Integers.len(val);
        if (dataLen == 1) {
            encodeLen1String((byte) val, bb);
            return;
        }
        // dataLen is 0 or 2-8
        bb.put((byte) (STRING_SHORT_OFFSET + dataLen));
        Integers.putLong(val, bb);
    }

    private static void encodeString(byte[] data, ByteBuffer bb) {
        final int dataLen = data.length;
        if (dataLen == 1) { // short string
            encodeLen1String(data[0], bb);
            return;
        }
        if (isShort(dataLen)) {
            bb.put((byte) (STRING_SHORT_OFFSET + dataLen)); // dataLen is 0 or 2-55
        } else { // long string
            bb.put((byte) (STRING_LONG_OFFSET + Integers.len(dataLen)));
            Integers.putLong(dataLen, bb);
        }
        bb.put(data);
    }

    private static void encodeLen1String(byte first, ByteBuffer bb) {
        if (first < 0x00) { // same as (first & 0xFF) >= 0x80
            bb.put((byte) (STRING_SHORT_OFFSET + 1));
        }
        bb.put(first);
    }

    private static void encodeList(int dataLen, Iterable<?> elements, ByteBuffer bb) {
        insertListPrefix(dataLen, bb);
        encodeSequentially(elements, bb);
    }
// ---------------------------------------------------------------------------------------------------------------------
    /**
     * Returns the RLP encoding of the given byte.
     *
     * @param b the byte to be encoded
     * @return the encoding
     */
    public static byte[] encode(byte b) {
        return encode(new byte[] { b });
    }

    /**
     * Returns the RLP encoding of the given byte string.
     *
     * @param byteString the byte string to be encoded
     * @return the encoding
     */
    public static byte[] encode(byte[] byteString) {
        ByteBuffer bb = ByteBuffer.allocate(stringEncodedLen(byteString));
        encodeString(byteString, bb);
        return bb.array();
    }

    /**
     * Returns the concatenation of the encodings of the given objects in the given order.
     *
     * @param objects the raw objects to be encoded in sequence
     * @return the encoded sequence
     */
    public static byte[] encodeSequentially(Object... objects) {
        return encodeSequentially(Arrays.asList(objects));
    }

    /**
     * Inserts the concatenation of the encodings of the given objects in the given order into the destination array.
     * The array containing the objects is <i>not</i> itself encoded.
     *
     * @param objects   the raw objects to be encoded
     * @param dest      the destination for the sequence of RLP encodings
     * @param destIndex the index into {@code dest} for the sequence
     * @return the index into {@code dest} marking the end of the sequence
     */
    public static int encodeSequentially(Object[] objects, byte[] dest, int destIndex) {
        return encodeSequentially(Arrays.asList(objects), dest, destIndex);
    }

    public static void encodeSequentially(Object[] objects, ByteBuffer dest) {
        encodeSequentially(Arrays.asList(objects), dest);
    }
//----------------------------------------------------------------------------------------------------------------------
    /**
     * Returns the concatenation of the encodings of the given objects in the given order. The {@link Iterable} containing
     * the objects is not itself encoded.
     *
     * @param objects the raw objects to be encoded
     * @return the encoded sequence
     */
    public static byte[] encodeSequentially(Iterable<?> objects) {
        byte[] dest = new byte[sumEncodedLen(objects)];
        encodeSequentially(objects, dest, 0);
        return dest;
    }

    /**
     * Inserts the concatenation of the encodings of the given objects in the given order into the destination array.
     * The {@link Iterable} containing the objects is not itself encoded.
     *
     * @param objects   the raw objects to be encoded
     * @param dest      the destination for the sequence of RLP encodings
     * @param destIndex the index into the destination for the sequence
     * @return the index into {@code dest} marking the end of the sequence
     */
    public static int encodeSequentially(Iterable<?> objects, byte[] dest, int destIndex) {
        ByteBuffer bb = ByteBuffer.wrap(dest, destIndex, dest.length - destIndex);
        encodeSequentially(objects, bb);
        return bb.position();
    }

    /**
     * Inserts the concatenation of the encodings of the given objects in the given order into the destination buffer.
     * The {@link Iterable} containing the objects is not itself encoded.
     *
     * @param objects the raw objects to be encoded
     * @param dest    the destination for the sequence of RLP encodings
     */
    public static void encodeSequentially(Iterable<?> objects, ByteBuffer dest) {
        for (Object raw : objects) {
            encodeItem(raw, dest);
        }
    }
//----------------------------------------------------------------------------------------------------------------------
    /**
     * Returns the encoding of an RLP list item containing the given elements encoded in the given order.
     *
     * @param elements the raw elements to be encoded as an RLP list item
     * @return the encoded RLP list item
     */
    public static byte[] encodeAsList(Object... elements) {
        return encodeAsList(Arrays.asList(elements));
    }

    /**
     * Inserts into the destination array the encoding of an RLP list item containing the elements of the array encoded
     * in the given order.
     *
     * @param elements  the raw elements to be encoded as an RLP list item
     * @param dest      the destination for the encoded RLP list
     * @param destIndex the index into the destination for the list
     */
    public static void encodeAsList(Object[] elements, byte[] dest, int destIndex) {
        encodeAsList(Arrays.asList(elements), dest, destIndex);
    }

    /**
     * Inserts into the destination buffer the encoding of an RLP list item containing the elements of the array encoded
     * in the given order.
     *
     * @param elements the raw elements to be encoded as an RLP list item
     * @param dest     the destination for the encoded RLP list
     */
    public static void encodeAsList(Object[] elements, ByteBuffer dest) {
        encodeAsList(Arrays.asList(elements), dest);
    }
//----------------------------------------------------------------------------------------------------------------------
    /**
     * Returns the encoding of an RLP list item containing the elements of the {@link Iterable} encoded in the given order.
     *
     * @param elements the raw elements to be encoded as an RLP list item
     * @return the encoded RLP list item
     */
    public static byte[] encodeAsList(Iterable<?> elements) {
        int dataLen = sumEncodedLen(elements);
        ByteBuffer bb = ByteBuffer.allocate(prefixLength(dataLen) + dataLen);
        encodeList(dataLen, elements, bb);
        return bb.array();
    }

    /**
     * Inserts into the destination array the encoding of an RLP list item, containing the elements of the {@link Iterable}
     * encoded in the given order.
     *
     * @param elements  the raw elements to be encoded as an RLP list item
     * @param dest      the destination for the encoded RLP list
     * @param destIndex the index into the destination for the list
     */
    public static void encodeAsList(Iterable<?> elements, byte[] dest, int destIndex) {
        encodeAsList(elements, ByteBuffer.wrap(dest, destIndex, dest.length - destIndex));
    }

    /**
     * Returns the encoding of an RLP list item containing the encoded elements of the given {@link Iterable} in the given
     * order.
     *
     * @param elements the raw elements to be encoded as an RLP list item
     * @param dest     the destination for the encoded RLP list
     */
    public static void encodeAsList(Iterable<?> elements, ByteBuffer dest) {
        encodeItem(elements, dest);
    }
//----------------------------------------------------------------------------------------------------------------------
    /**
     * Wraps n encodings in an {@link RLPList}.
     *
     * @param encodings the RLP-encoded elements of the new RLPList
     * @return the RLPList containing the given elements
     */
    public static RLPList toList(RLPItem... encodings) {
        return toList(Arrays.asList(encodings));
    }

    /**
     * Wraps n encodings in an {@link RLPList}.
     *
     * @param encodings the RLP-encoded elements of the new RLPList
     * @return the RLPList containing the given elements
     */
    public static RLPList toList(Iterable<RLPItem> encodings) {
        return RLPList.withElements(encodings);
    }
}
