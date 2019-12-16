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

import com.esaulpaugh.headlong.abi.util.Integers;
import com.esaulpaugh.headlong.abi.util.BizarroIntegers;
import com.esaulpaugh.headlong.util.FastHex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.*;

public class PackedEncoderTest {

    @Test
    public void testPacked() throws ParseException {

        TupleType tupleType = TupleType.parse("(int16,bytes1,uint16,string)");

        Tuple test = new Tuple(-1, new byte[] { 0x42 }, 0x03, "Hello, world!");

        tupleType.validate(test);

        int packedLen = tupleType.byteLengthPacked(test);

        assertEquals(FastHex.decode("ffff42000348656c6c6f2c20776f726c6421").length, packedLen);

        ByteBuffer dest = ByteBuffer.allocate(packedLen);

        tupleType.encodePacked(test, dest);

        byte[] destArray = dest.array();

        System.out.println(FastHex.encodeToString(destArray));

        assertArrayEquals(FastHex.decode("ffff42000348656c6c6f2c20776f726c6421"), destArray);

        // ---------------------------

        Function function = new Function(tupleType.canonicalType);

        String hex = FastHex.encodeToString(function.getParamTypes().encode(test).array());

        System.out.println(hex);

        byte[] abi = FastHex.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff420000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000d48656c6c6f2c20776f726c642100000000000000000000000000000000000000");

        byte[] call = new byte[Function.SELECTOR_LEN + abi.length];

        System.arraycopy(function.selector(), 0, call, 0, Function.SELECTOR_LEN);
        System.arraycopy(abi, 0, call, Function.SELECTOR_LEN, abi.length);

        Tuple args = function.decodeCall(call);

        TupleType tt = TupleType.parse(tupleType.canonicalType);

        ByteBuffer dest2 = tt.encodePacked(args);
        byte[] dest2Array = dest2.array();

        System.out.println(FastHex.encodeToString(dest2Array));

        assertArrayEquals(FastHex.decode("ffff42000348656c6c6f2c20776f726c6421"), dest2Array);

    }

    @Test
    public void testTest() throws ParseException {

        TupleType tupleType = TupleType.parse("(int24,bool,bool)");

        Tuple values = new Tuple(-2, true, false);

        tupleType.validate(values);

        ByteBuffer packed = tupleType.encodePacked(values);
        byte[] packedArray = packed.array();

        System.out.println(FastHex.encodeToString(packedArray));

        assertArrayEquals(FastHex.decode("fffffe0100"), packedArray);

        Tuple decoded = PackedDecoder.decode(tupleType, packedArray);

        assertEquals(values, decoded);
    }

    @Test
    public void testDecodeA() throws ParseException {

        TupleType tupleType = TupleType.parse("(uint64[],uint64[1],uint64,int72)");

        Tuple values = new Tuple( new long[] { 9L }, new long[] { 5L }, BigInteger.valueOf(6L), BigInteger.valueOf(-1L));

        tupleType.validate(values);

        ByteBuffer packed = tupleType.encodePacked(values);
        byte[] packedArray = packed.array();

        System.out.println(FastHex.encodeToString(packedArray));

        assertArrayEquals(FastHex.decode("000000000000000900000000000000050000000000000006ffffffffffffffffff"), packedArray);

        Tuple decoded = PackedDecoder.decode(tupleType, packedArray);

        assertEquals(values, decoded);
    }

    @Test
    public void testDecodeB() throws ParseException {

        TupleType tupleType = TupleType.parse("(uint64[],int)");

        Tuple values = new Tuple(new long[] { 1L, 2L, 3L, 4L }, BigInteger.ONE);

        tupleType.validate(values);

        ByteBuffer packed = tupleType.encodePacked(values);
        byte[] packedArray = packed.array();

        System.out.println(FastHex.encodeToString(packedArray));

        assertArrayEquals(FastHex.decode("00000000000000010000000000000002000000000000000300000000000000040000000000000000000000000000000000000000000000000000000000000001"), packedArray);

        Tuple decoded = PackedDecoder.decode(tupleType, packedArray);

        assertEquals(values, decoded);
    }

    @Test
    public void testDecodeC() throws ParseException {
        TupleType tupleType = TupleType.parse("(bool[],bool)");

        Tuple values = new Tuple(new boolean[] { true, false, true }, true);

        tupleType.validate(values);

        ByteBuffer packed = tupleType.encodePacked(values);
        byte[] packedArray = packed.array();

        System.out.println(FastHex.encodeToString(packedArray));

        assertArrayEquals(FastHex.decode("01000101"), packedArray);

        Tuple decoded = PackedDecoder.decode(tupleType, packedArray);

        assertEquals(values, decoded);
    }

    @Test
    public void testSignExtendInt() {
        int expected = BizarroIntegers.getInt(FastHex.decode("8FFFFF"), 0, 3);
        int result = Integers.getPackedInt(FastHex.decode("8FFFFF"), 0, 3);
        assertTrue(result < 0);
        assertEquals(expected, result);
    }

    @Test
    public void testSignExtendLong() {
        long expectedL = BizarroIntegers.getLong(FastHex.decode("8FFFFFFFFF"), 0, 5);
        long resultL = Integers.getPackedLong(FastHex.decode("8FFFFFFFFF"), 0, 5);
        assertTrue(resultL < 0);
        assertEquals(expectedL, resultL);
    }
}
