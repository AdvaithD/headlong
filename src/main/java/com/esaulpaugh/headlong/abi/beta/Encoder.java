package com.esaulpaugh.headlong.abi.beta;

import com.esaulpaugh.headlong.abi.beta.util.Tuple;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static com.esaulpaugh.headlong.abi.beta.AbstractUnitType.UNIT_LENGTH_BYTES;
import static com.esaulpaugh.headlong.abi.beta.Function.SELECTOR_LEN;
import static com.esaulpaugh.headlong.abi.beta.StackableType.*;
import static java.nio.charset.StandardCharsets.UTF_8;

// TODO use switch(type.getInt()) and unchecked cast instead of repeated instanceof
class Encoder {

    static final int OFFSET_LENGTH_BYTES = UNIT_LENGTH_BYTES;
    static final IntType OFFSET_TYPE = new IntType("uint32", IntType.MAX_BIT_LEN, false);

    private static final byte NEGATIVE_ONE_BYTE = (byte) 0xFF;
    private static final byte ZERO_BYTE = (byte) 0;

    private static final byte[] BOOLEAN_FALSE = new byte[UNIT_LENGTH_BYTES];
    private static final byte[] BOOLEAN_TRUE = new byte[UNIT_LENGTH_BYTES];

    private static final byte[] NON_NEGATIVE_INT_PADDING = new byte[24];
    private static final byte[] NEGATIVE_INT_PADDING = new byte[24];

    static {
        BOOLEAN_TRUE[BOOLEAN_TRUE.length-1] = 1;
        Arrays.fill(NEGATIVE_INT_PADDING, NEGATIVE_ONE_BYTE);
    }

    static ByteBuffer encodeFunctionCall(Function function, Tuple argsTuple) {

        final TupleType tupleType = function.paramTypes;
        final StackableType<?>[] types = tupleType.elementTypes;

        if(argsTuple.elements.length != types.length) {
            throw new IllegalArgumentException("argsTuple.elements.length <> types.length: " + argsTuple.elements.length + " != " + types.length);
        }

        tupleType.validate(argsTuple);

        final int allocation = SELECTOR_LEN + tupleType.byteLength(argsTuple);
        ByteBuffer outBuffer = ByteBuffer.wrap(new byte[allocation]); // ByteOrder.BIG_ENDIAN by default

        outBuffer.put(function.selector);
        insertTuple(tupleType, argsTuple, outBuffer);

        return outBuffer;
    }

    private static void insertTuple(TupleType tupleType, Tuple tuple, ByteBuffer outBuffer) {

        LinkedList<StackableType<?>> typeList = new LinkedList<>(Arrays.asList(tupleType.elementTypes));
        LinkedList<Object> valuesList = new LinkedList<>(Arrays.asList(tuple.elements));

        encodeHeadsForTuple(typeList, valuesList, headLengthSum(tupleType, tuple), outBuffer);
        encodeTailsForTuple(typeList, valuesList, outBuffer);
    }

    private static void encodeHeadsForTuple(LinkedList<StackableType<?>> types, LinkedList<Object> values, int headLengthSum, ByteBuffer outBuffer) {

        final int[] offset = new int[] { headLengthSum };

        Iterator<StackableType<?>> ti = types.iterator();
        Iterator<Object> vi = values.iterator();

        while(ti.hasNext()) {

            StackableType<?> type = ti.next();
            Object val = vi.next();

            encodeHead(type, val, outBuffer, offset);

            if(!type.dynamic) {
                ti.remove();
                vi.remove();
            }
        }
    }

    private static void encodeHead(StackableType<?> paramType, Object value, ByteBuffer dest, int[] offset) {

        switch (paramType.typeCode()) {
        case TYPE_CODE_BOOLEAN: insertBool((boolean) value, dest); return;
        case TYPE_CODE_BYTE:
        case TYPE_CODE_SHORT:
        case TYPE_CODE_INT:
        case TYPE_CODE_LONG: insertInt(((Number) value).longValue(), dest); return;
        case TYPE_CODE_BIG_INTEGER: insertInt(((BigInteger) value), dest); return;
        case TYPE_CODE_BIG_DECIMAL: insertInt(((BigDecimal) value).unscaledValue(), dest); return;
        case TYPE_CODE_ARRAY:
            if(paramType.dynamic) { // includes String
                insertOffset(offset, paramType, value, dest);
            } else {
                encodeArrayStatic((ArrayType) paramType, value, dest);
            }
            return;
        case TYPE_CODE_TUPLE: insertTupleHead((TupleType) paramType, (Tuple) value, dest, offset); return;
        default: throw new IllegalArgumentException("unexpected array type: " + paramType.toString());
        }
    }

    private static void insertTupleHead(TupleType tupleType, Tuple tuple, ByteBuffer dest, int[] offset) {
        if(tupleType.dynamic) {
            insertOffset(offset, tupleType, tuple, dest);
        } else {
            insertTuple(tupleType, tuple, dest);
        }
    }

    private static void insertLength(int length, ByteBuffer dest) {
        insertInt(length, dest);
    }

    private static void insertOffset(final int[] offset, StackableType<?> paramType, Object object, ByteBuffer dest) {
        insertInt(offset[0], dest);
        offset[0] += paramType.byteLength(object);
    }

    private static void encodeTailsForTuple(List<StackableType<?>> types, List<Object> values, ByteBuffer outBuffer) {
        Iterator<StackableType<?>> ti;
        Iterator<Object> vi;
        for(ti = types.iterator(), vi = values.iterator(); ti.hasNext(); ) {
            StackableType<?> type = ti.next();
            encodeTail(type, vi.next(), outBuffer);
        }
    }

    private static void encodeTail(StackableType<?> type, Object value, ByteBuffer dest) {
//        only dynamics expected
        switch (type.typeCode()) {
        case TYPE_CODE_ARRAY:
            ArrayType arrayType = (ArrayType) type;
            if(arrayType.isString) {
                byte[] bytes = ((String) value).getBytes(UTF_8);
                insertLength(bytes.length, dest);
                insertBytes(bytes, dest);
            } else {
                encodeArrayTail(arrayType, value, dest);
            }
            return;
        case TYPE_CODE_TUPLE:
            insertTuple((TupleType) type, (Tuple) value, dest);
            return;
        default:
            throw new Error("unexpected type: " + type.toString());
        }
    }

    // ----------------------------------------------

    private static void encodeArrayStatic(ArrayType arrayType, Object value, ByteBuffer dest) {
        switch (arrayType.elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: insertBooleans((boolean[]) value, dest); return;
        case TYPE_CODE_BYTE: insertBytes((byte[]) value, dest); return; // strings are dynamic, so not expected
        case TYPE_CODE_SHORT: insertShorts((short[]) value, dest); return;
        case TYPE_CODE_INT: insertInts((int[]) value, dest); return;
        case TYPE_CODE_LONG: insertLongs((long[]) value, dest); return;
        case TYPE_CODE_BIG_INTEGER: insertBigIntegers((BigInteger[]) value, dest); return;
        case TYPE_CODE_BIG_DECIMAL: insertBigDecimals((BigDecimal[]) value, dest); return;
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE:
            final StackableType<?> elementType = arrayType.elementType;
            for(Object e : (Object[]) value) {
                encodeHead(elementType, e, dest, null);
            }
            return;
        default: throw new IllegalArgumentException("unexpected array type: " + arrayType.toString());
        }
    }

    private static void encodeArrayTail(ArrayType arrayType, Object value, ByteBuffer dest) {
        if(arrayType.dynamic) {
            insertLength(Array.getLength(value), dest);
        }
        final StackableType<?> elementType = arrayType.elementType;
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: insertBooleans((boolean[]) value, dest); return;
        case TYPE_CODE_BYTE: insertBytes((byte[]) value, dest); return;
        case TYPE_CODE_SHORT: insertShorts((short[]) value, dest); return;
        case TYPE_CODE_INT: insertInts((int[]) value, dest); return;
        case TYPE_CODE_LONG: insertLongs((long[]) value, dest); return;
        case TYPE_CODE_BIG_INTEGER: insertBigIntegers((BigInteger[]) value, dest); return;
        case TYPE_CODE_BIG_DECIMAL: insertBigDecimals((BigDecimal[]) value, dest); return;
        case TYPE_CODE_ARRAY:  // type for String[] has TYPE_CODE_ARRAY
        case TYPE_CODE_TUPLE:
            Object[] objects = (Object[]) value;
            if (elementType.dynamic) { // if elements are dynamic
                final int[] offset = new int[] { objects.length << 5 }; // mul 32 (0x20)
                for (Object element : objects) {
                    insertOffset(offset, elementType, element, dest);
                }
            }
            for (Object element : objects) {
                encodeTail(elementType, element, dest);
            }
            return;
        default:
            throw new IllegalArgumentException("unexpected array type: " + arrayType.toString());
        }
    }

    // ========================================

    private static int headLengthSum(TupleType tupleType, Tuple tuple) {
        StackableType<?>[] elementTypes = tupleType.elementTypes;
        Object[] elements = tuple.elements;
        int headLengths = 0;
        final int n = elementTypes.length;
        for (int i = 0; i < n; i++) {
            StackableType<?> t = elementTypes[i];
            if(t.dynamic) {
                headLengths += 32;
            } else {
                headLengths += t.byteLength(elements[i]);
            }
        }

        return headLengths;
    }

    // -------------------------------------------------------------------------------------------------

    private static void insertBooleans(boolean[] bools, ByteBuffer dest) {
        for (boolean e : bools) {
            dest.put(e ? BOOLEAN_TRUE : BOOLEAN_FALSE);
        }
    }

    private static int paddingLength(int len) {
        int mod = len & 31;
        return mod == 0
                ? 0
                : 32 - mod;
    }

    private static void insertBytes(byte[] bytes, ByteBuffer dest) {
        dest.put(bytes);
        final int paddingLength = paddingLength(bytes.length);
        for (int i = 0; i < paddingLength; i++) {
            dest.put(ZERO_BYTE);
        }
    }

    private static void insertShorts(short[] shorts, ByteBuffer dest) {
        for (short e : shorts) {
            insertInt(e, dest);
        }
    }

    private static void insertInts(int[] ints, ByteBuffer dest) {
        for (int e : ints) {
            insertInt(e, dest);
        }
    }

    private static void insertLongs(long[] longs, ByteBuffer dest) {
        for (long e : longs) {
            insertInt(e, dest);
        }
    }

    private static void insertBigIntegers(BigInteger[] bigInts, ByteBuffer dest) {
        for (BigInteger e : bigInts) {
            insertInt(e, dest);
        }
    }

    private static void insertBigDecimals(BigDecimal[] bigDecs, ByteBuffer dest) {
        for (BigDecimal e : bigDecs) {
            insertInt(e.unscaledValue(), dest);
        }
    }

    // ------------------------------------------------------------------------------

    private static void insertInt(long val, ByteBuffer dest) {
        dest.put(val < 0 ? NEGATIVE_INT_PADDING : NON_NEGATIVE_INT_PADDING);
        dest.putLong(val);
    }

    private static void insertInt(BigInteger bigGuy, ByteBuffer dest) {
        byte[] arr = bigGuy.toByteArray();
        final byte paddingByte = bigGuy.signum() == -1 ? NEGATIVE_ONE_BYTE : ZERO_BYTE;
        final int lim = 32 - arr.length;
        for (int i = 0; i < lim; i++) {
            dest.put(paddingByte);
        }
        dest.put(arr);
    }

    private static void insertBool(boolean bool, ByteBuffer dest) {
        dest.put(bool ? BOOLEAN_TRUE : BOOLEAN_FALSE);
//        insertInt(bool ? 1L : 0L, dest);
    }
}
