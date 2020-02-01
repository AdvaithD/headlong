/*
   Copyright 2020 Evan Saulpaugh

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
package com.esaulpaugh.headlong.util;

import com.esaulpaugh.headlong.abi.ABIException;
import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.ArrayType;
import com.esaulpaugh.headlong.abi.BigDecimalType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.exception.DecodeException;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.rlp.RLPList;
import com.esaulpaugh.headlong.rlp.RLPString;
import com.esaulpaugh.headlong.rlp.util.Notation;
import com.esaulpaugh.headlong.rlp.util.NotationParser;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;

import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_ARRAY;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BIG_DECIMAL;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BIG_INTEGER;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BOOLEAN;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BYTE;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_INT;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_LONG;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_TUPLE;
import static com.esaulpaugh.headlong.rlp.RLPDecoder.RLP_STRICT;

public class SuperSerial {

    private static final byte[] TRUE = new byte[] { 0x01 };
    private static final byte[] FALSE = new byte[] { 0x00 };

    public static String serialize(TupleType tupleType, Tuple tuple, boolean machine) throws ABIException, DecodeException {
        tupleType.validate(tuple);
        Object[] objects = serializeTuple(tupleType, tuple);
        return machine
                ? Strings.encode(RLPEncoder.encodeSequentially(objects))
                : Notation.forObjects(objects).toString();
    }

    public static Tuple deserialize(TupleType tupleType, String str, boolean machine) throws DecodeException, ABIException {
        Tuple tuple = deserializeTuple(
                tupleType,
                machine ?
                        Strings.decode(str)
                        : RLPEncoder.encodeSequentially(NotationParser.parse(str))
        );
        tupleType.validate(tuple);
        return tuple;
    }

    private static Object[] serializeTuple(TupleType tupleType, Object obj) throws ABIException {
        Tuple tuple = (Tuple) obj;
        final int len = tupleType.size();
        Object[] out = new Object[len];
        for(int i = 0; i < len; i++) {
            out[i] = serialize(tupleType.get(i), tuple.get(i));
        }
        return out;
    }

    private static Tuple deserializeTuple(TupleType tupleType, byte[] sequence) throws DecodeException {
        Iterator<RLPItem> sequenceIterator = RLP_STRICT.sequenceIterator(sequence);
        final int len = tupleType.size();
        Object[] elements = new Object[len];
        for(int i = 0; i < len; i++) {
            elements[i] = deserialize(tupleType.get(i), sequenceIterator.next());
        }
        return new Tuple(elements);
    }

    private static Object serialize(ABIType<?> type, Object obj) throws ABIException {
        switch (type.typeCode()) {
        case TYPE_CODE_BOOLEAN: return (boolean) obj ? TRUE : FALSE;
        case TYPE_CODE_BYTE: return Integers.toBytes((byte) obj); // case currently goes unused
        case TYPE_CODE_INT: return Integers.toBytes((int) obj);
        case TYPE_CODE_LONG: return Integers.toBytes((long) obj);
        case TYPE_CODE_BIG_INTEGER: return ((BigInteger) obj).toByteArray();
        case TYPE_CODE_BIG_DECIMAL: return ((BigDecimal) obj).unscaledValue().toByteArray();
        case TYPE_CODE_ARRAY: return serializeArray((ArrayType<? extends ABIType<?>, ?>) type, obj);
        case TYPE_CODE_TUPLE: return serializeTuple((TupleType) type, obj);
        default: throw new Error();
        }
    }

    private static Object deserialize(ABIType<?> type, RLPItem item) throws DecodeException {
        switch (type.typeCode()) {
        case TYPE_CODE_BOOLEAN: return item.asBoolean();
        case TYPE_CODE_BYTE: return item.asByte(); // case currently goes unused
        case TYPE_CODE_INT: return item.asInt();
        case TYPE_CODE_LONG: return item.asLong();
        case TYPE_CODE_BIG_INTEGER: return item.asBigInt();
        case TYPE_CODE_BIG_DECIMAL: return item.asBigDecimal(((BigDecimalType) type).getScale());
        case TYPE_CODE_ARRAY: return deserializeArray((ArrayType<? extends ABIType<?>, ?>) type, item);
        case TYPE_CODE_TUPLE: return deserializeTuple((TupleType) type, item.asBytes());
        default: throw new Error();
        }
    }

    private static Object serializeArray(ArrayType<? extends ABIType<?>, ?> arrayType, Object arr) throws ABIException {
        ABIType<?> elementType = arrayType.getElementType();
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: return serializeBooleanArray(arr);
        case TYPE_CODE_BYTE: return serializeByteArray(arrayType, arr);
        case TYPE_CODE_INT: return serializeIntArray(arr);
        case TYPE_CODE_LONG: return serializeLongArray(arr);
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL:
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE: return serializeObjectArray(arr, elementType);
        default: throw new Error();
        }
    }

    private static Object deserializeArray(ArrayType<? extends ABIType<?>,?> arrayType, RLPItem item) throws DecodeException {
        ABIType<?> elementType = arrayType.getElementType();
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: return deserializeBooleanArray((RLPList) item);
        case TYPE_CODE_BYTE: return deserializeByteArray(arrayType, (RLPString) item);
        case TYPE_CODE_INT: return deserializeIntArray((RLPList) item);
        case TYPE_CODE_LONG: return deserializeLongArray((RLPList) item);
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL:
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE: return deserializeObjectArray(elementType, (RLPList) item);
        default: throw new Error();
        }
    }

    private static byte[][] serializeBooleanArray(Object arr) {
        boolean[] booleans = (boolean[]) arr;
        final int len = booleans.length;
        byte[][] out = new byte[len][];
        for (int i = 0; i < len; i++) {
            out[i] = Integers.toBytes(booleans[i] ? (byte) 0x01 : (byte) 0x00);
        }
        return out;
    }

    private static boolean[] deserializeBooleanArray(RLPList list) throws DecodeException {
        List<RLPItem> elements = list.elements(RLP_STRICT);
        boolean[] booleans = new boolean[elements.size()];
        int i = 0;
        for (RLPItem e : elements) {
            booleans[i++] = e.asBoolean();
        }
        return booleans;
    }

    private static byte[] serializeByteArray(ArrayType<? extends ABIType<?>,?> arrayType, Object arr) {
        return arrayType.isString() ? Strings.decode((String) arr, Strings.UTF_8) : (byte[]) arr;
    }

    private static Object deserializeByteArray(ArrayType<? extends ABIType<?>,?> arrayType, RLPString string) {
        return arrayType.isString() ? string.asString(Strings.UTF_8) : string.asBytes();
    }

    private static byte[][] serializeIntArray(Object arr) {
        int[] ints = (int[]) arr;
        final int len = ints.length;
        byte[][] list = new byte[len][];
        for (int i = 0; i < len; i++) {
            list[i] = Integers.toBytes(ints[i]);
        }
        return list;
    }

    private static int[] deserializeIntArray(RLPList list) throws DecodeException {
        List<RLPItem> elements = list.elements(RLP_STRICT);
        int[] ints = new int[elements.size()];
        int i = 0;
        for (RLPItem e : elements) {
            ints[i++] = e.asInt();
        }
        return ints;
    }

    private static byte[][] serializeLongArray(Object arr) {
        long[] longs = (long[]) arr;
        final int len = longs.length;
        byte[][] list = new byte[len][];
        for (int i = 0; i < len; i++) {
            list[i] = Integers.toBytes(longs[i]);
        }
        return list;
    }

    private static long[] deserializeLongArray(RLPList list) throws DecodeException {
        List<RLPItem> elements = list.elements(RLP_STRICT);
        long[] longs = new long[elements.size()];
        int i = 0;
        for (RLPItem e : elements) {
            longs[i++] = e.asLong();
        }
        return longs;
    }

    private static Object[] serializeObjectArray(Object arr, ABIType<?> elementType) throws ABIException {
        Object[] objects = (Object[]) arr;
        final int len = objects.length;
        Object[] list = new Object[len];
        for (int i = 0; i < len; i++) {
            list[i] = serialize(elementType, objects[i]);
        }
        return list;
    }

    private static Object[] deserializeObjectArray(ABIType<?> elementType, RLPList list) throws DecodeException {
        List<RLPItem> elements = list.elements(RLP_STRICT);
        Object[] objects = (Object[]) Array.newInstance(elementType.clazz(), elements.size()); // reflection ftw
        int i = 0;
        for (RLPItem e : elements) {
            objects[i++] = deserialize(elementType, e);
        }
        return objects;
    }
}
