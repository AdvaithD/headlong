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

import com.esaulpaugh.headlong.rlp.eip778.KeyValuePair;
import com.esaulpaugh.headlong.util.Integers;

import java.util.Arrays;
import java.util.List;

import static com.esaulpaugh.headlong.rlp.DataType.LIST_LONG_OFFSET;
import static com.esaulpaugh.headlong.rlp.DataType.LIST_SHORT_OFFSET;
import static com.esaulpaugh.headlong.rlp.DataType.MIN_LONG_DATA_LEN;
import static com.esaulpaugh.headlong.rlp.DataType.STRING_LONG_OFFSET;
import static com.esaulpaugh.headlong.rlp.DataType.STRING_SHORT_OFFSET;

/** For encoding data to Recursive Length Prefix format. */
public final class RLPEncoder {
// -------------- MADE VISIBLE TO rlp.eip778 package --------------
    public static int insertRecordContentList(int dataLen, long seq, List<KeyValuePair> pairs, byte[] record, int offset) {
        if (seq < 0) {
            throw new IllegalArgumentException("negative seq");
        }
        pairs.sort(KeyValuePair.PAIR_COMPARATOR);
        offset = encodeString(seq, record, insertListPrefix(dataLen, record, offset));
        for (KeyValuePair pair : pairs) {
            offset = encodeKeyValuePair(pair, record, offset);
        }
        return offset;
    }

    public static int insertRecordSignature(byte[] signature, byte[] record, int offset) {
        return encodeItem(signature, record, offset);
    }

    public static long encodedLen(long val) {
        int dataLen = Integers.len(val);
        if (dataLen == 1) {
            return (byte) val >= 0x00 ? 1 : 2;
        }
        return 1 + dataLen;
    }

    public static long dataLen(List<KeyValuePair> pairs) {
        long total = 0;
        for (KeyValuePair kvp : pairs) {
            total += stringEncodedLen(kvp.getKey()) + stringEncodedLen(kvp.getValue());
        }
        return total;
    }

    public static int prefixLength(long dataLen) {
        if (isLong(dataLen)) {
            return 1 + Integers.len(dataLen);
        } else {
            return 1;
        }
    }

    public static int insertListPrefix(long dataLen, byte[] dest, int destIndex) {
        return isLong(dataLen)
                ? encodeLongListPrefix(dataLen, dest, destIndex)
                : encodeShortListPrefix(dataLen, dest, destIndex);
    }
// ----------------------------------------------------------------
    private static boolean isLong(long dataLen) {
        return dataLen >= MIN_LONG_DATA_LEN;
    }

    private static long sumEncodedLen(Iterable<?> rawItems) {
        long sum = 0;
        for (Object raw : rawItems) {
            sum += encodedLen(raw);
        }
        return sum;
    }

    private static long encodedLen(Object raw) {
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
        if (isLong(dataLen)) {
            return 1 + Integers.len(dataLen) + dataLen;
        }
        if (dataLen == 1 && byteString[0] >= 0x00) { // same as (bytes[0] & 0xFF) < 0x80
            return 1;
        }
        return 1 + dataLen;
    }

    private static long listEncodedLen(Iterable<?> items) {
        final long listDataLen = sumEncodedLen(items);
        if (isLong(listDataLen)) {
            return 1 + Integers.len(listDataLen) + listDataLen;
        }
        return 1 + listDataLen;
    }

    private static int encodeKeyValuePair(KeyValuePair pair, byte[] dest, int destIndex) {
        return encodeString(pair.getValue(), dest, encodeString(pair.getKey(), dest, destIndex));
    }

    private static int encodeItem(Object raw, byte[] dest, int destIndex) {
        if (raw instanceof byte[]) {
            return encodeString((byte[]) raw, dest, destIndex);
        }
        if (raw instanceof Iterable<?>) {
            Iterable<?> elements = (Iterable<?>) raw;
            return encodeList(sumEncodedLen(elements), elements, dest, destIndex);
        }
        if(raw instanceof Object[]) {
            Iterable<Object> elements = Arrays.asList((Object[]) raw);
            return encodeList(sumEncodedLen(elements), elements, dest, destIndex);
        }
        if(raw == null) {
            throw new NullPointerException(); // TODO correct behavior?
        }
        throw new IllegalArgumentException("unsupported object type: " + raw.getClass().getName());
    }

    private static int encodeString(long val, byte[] dest, int destIndex) {
        final int dataLen = Integers.len(val);
        // short string
        if (dataLen == 1) {
            return encodeLen1String((byte) val, dest, destIndex);
        }
        // dataLen is 0 or 2-8
        dest[destIndex++] = (byte) (STRING_SHORT_OFFSET + dataLen);
        Integers.putLong(val, dest, destIndex);
        return destIndex + dataLen;
    }

    private static int encodeString(byte[] data, byte[] dest, int destIndex) {
        final int dataLen = data.length;
        // short string
        if (dataLen == 1) {
            return encodeLen1String(data[0], dest, destIndex);
        }
        if (isLong(dataLen)) { // long string
            int lengthOfLength = Integers.putLong(dataLen, dest, destIndex + 1);
            dest[destIndex] = (byte) (STRING_LONG_OFFSET + lengthOfLength);
            destIndex += 1 + lengthOfLength;
        } else {
            dest[destIndex++] = (byte) (STRING_SHORT_OFFSET + dataLen); // dataLen is 0 or 2-55
        }
        System.arraycopy(data, 0, dest, destIndex, dataLen);
        return destIndex + dataLen;
    }

    private static int encodeLen1String(byte first, byte[] dest, int destIndex) {
        if (first < 0x00) { // same as (first & 0xFF) >= 0x80
            dest[destIndex++] = (byte) (STRING_SHORT_OFFSET + 1);
        }
        dest[destIndex++] = first;
        return destIndex;
    }

    private static int encodeList(long dataLen, Iterable<?> elements, byte[] dest, int destIndex) {
        destIndex = insertListPrefix(dataLen, dest, destIndex);
        return encodeSequentially(elements, dest, destIndex);
    }

    private static int encodeLongListPrefix(final long dataLen, byte[] dest, final int destIndex) {
        final int lengthIndex = destIndex + 1;
        final int n = Integers.putLong(dataLen, dest, lengthIndex);
        dest[destIndex] = (byte) (LIST_LONG_OFFSET + (byte) n);
        return lengthIndex + n;
    }

    private static int encodeShortListPrefix(final long dataLen, byte[] dest, final int destIndex) {
        dest[destIndex] = (byte) (LIST_SHORT_OFFSET + (byte) dataLen);
        return destIndex + 1;
    }
    // -----------------------------------------------------------------------------------------------------------------
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
        byte[] dest = new byte[stringEncodedLen(byteString)];
        encodeString(byteString, dest, 0);
        return dest;
    }

    /**
     * Returns the concatenation of the encodings of the given objects in the given order.
     *
     * @param objects the raw objects to be encoded in sequence
     * @return the encoded sequence
     */
    public static byte[] encodeSequentially(Object... objects) {
        byte[] dest = new byte[(int) sumEncodedLen(Arrays.asList(objects))];
        encodeSequentially(objects, dest, 0);
        return dest;
    }

    /**
     * Returns the concatenation of the encodings of the given objects in the given order. The {@link Iterable}
     * containing the objects is <i>not</i> encoded.
     *
     * @param objects the raw objects to be encoded
     * @return the encoded sequence
     */
    public static byte[] encodeSequentially(Iterable<?> objects) {
        byte[] dest = new byte[(int) sumEncodedLen(objects)];
        encodeSequentially(objects, dest, 0);
        return dest;
    }

    /**
     * Inserts the concatenation of the encodings of the given objects in the given order into the destination array.
     * The array containing the objects is <i>not</i> encoded.
     *
     * @param objects   the raw objects to be encoded
     * @param dest      the destination for the sequence of RLP encodings
     * @param destIndex the index into {@code dest} for the sequence
     * @return the index into {@code dest} marking the end of the sequence
     */
    public static int encodeSequentially(Object[] objects, byte[] dest, int destIndex) {
        if(objects instanceof KeyValuePair[]) {
            for (KeyValuePair kvp : (KeyValuePair[]) objects) {
                destIndex = encodeKeyValuePair(kvp, dest, destIndex);
            }
        } else {
            for (Object item : objects) {
                destIndex = encodeItem(item, dest, destIndex);
            }
        }
        return destIndex;
    }

    /**
     * Inserts the concatenation of the encodings of the given objects in the given order into the destination array.
     * The {@code Iterable} containing the objects is <i>not</i> encoded.
     *
     * @param objects   the raw objects to be encoded
     * @param dest      the destination for the sequence of RLP encodings
     * @param destIndex the index into the destination for the sequence
     * @return the index marking the end of the sequence
     */
    public static int encodeSequentially(Iterable<?> objects, byte[] dest, int destIndex) {
        for (Object obj : objects) {
            destIndex = encodeItem(obj, dest, destIndex);
        }
        return destIndex;
    }

    /**
     * Returns the encoding of an RLP list item containing the given objects, encoded.
     *
     * @param elements the raw elements to be encoded as an RLP list item
     * @return the encoded RLP list item
     */
    public static byte[] encodeAsList(Object... elements) {
        return encodeAsList(Arrays.asList(elements));
    }

    /**
     * Returns the encoding of an RLP list item containing the encoded elements of the given {@link Iterable} in the
     * given order.
     *
     * @param elements the raw elements to be encoded as an RLP list item
     * @return the encoded RLP list item
     */
    public static byte[] encodeAsList(Iterable<?> elements) {
        long listDataLen = sumEncodedLen(elements);
        byte[] dest = new byte[prefixLength(listDataLen) + (int) listDataLen];
        encodeList(listDataLen, elements, dest, 0);
        return dest;
    }

    /**
     * Inserts the encoding of an RLP list item, containing the encoded elements of the array in the given order, into
     * the destination array.
     *
     * @param elements  the raw elements to be encoded as an RLP list item
     * @param dest      the destination for the RLP encoding of the list
     * @param destIndex the index into the destination for the list
     */
    public static void encodeAsList(Object[] elements, byte[] dest, int destIndex) {
        encodeAsList(Arrays.asList(elements), dest, destIndex);
    }

    /**
     * Inserts the encoding of an RLP list item, containing the encoded elements of the {@link Iterable} in the given
     * order, into the destination array.
     *
     * @param elements  the raw elements to be encoded as an RLP list item
     * @param dest      the destination for the RLP encoding of the list
     * @param destIndex the index into the destination for the list
     */
    public static void encodeAsList(Iterable<?> elements, byte[] dest, int destIndex) {
        long listDataLen = sumEncodedLen(elements);
        encodeList(listDataLen, elements, dest, destIndex);
    }

    /**
     * Wraps n encodings in an RLPList.
     *
     * @param encodings the RLP-encoded elements of the new RLPList
     * @return the RLPList containing the given elements
     */
    public static RLPList toList(RLPItem... encodings) {
        return toList(Arrays.asList(encodings));
    }

    /**
     * Wraps n encodings in an RLPList.
     *
     * @param encodings the RLP-encoded elements of the new RLPList
     * @return the RLPList containing the given elements
     */
    public static RLPList toList(Iterable<RLPItem> encodings) {
        return RLPList.withElements(encodings);
    }
}
