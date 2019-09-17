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

import com.esaulpaugh.headlong.abi.util.BizarroIntegers;
import com.esaulpaugh.headlong.rlp.util.Integers;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.esaulpaugh.headlong.abi.UnitType.UNIT_LENGTH_BYTES;

final class Encoding {

    static final int OFFSET_LENGTH_BYTES = UNIT_LENGTH_BYTES;
    static final IntType OFFSET_TYPE = new IntType("int32", Integer.SIZE, false); // uint256

    static final byte NEGATIVE_ONE_BYTE = (byte) 0xFF;
    static final byte ZERO_BYTE = (byte) 0;

    private static final byte[] NON_NEGATIVE_INT_PADDING = new byte[24];
    private static final byte[] NEGATIVE_INT_PADDING = new byte[24];

    static {
        Arrays.fill(NEGATIVE_INT_PADDING, NEGATIVE_ONE_BYTE);
    }

    @SuppressWarnings("unchecked")
    static void insertOffset(final int[] offset, ABIType paramType, Object object, ByteBuffer dest) {
        final int val = offset[0];
        insertInt(val, dest);
        offset[0] = val + paramType.byteLength(object);
    }

    static void insertInt(long val, ByteBuffer dest) {
        dest.put(val < 0 ? NEGATIVE_INT_PADDING : NON_NEGATIVE_INT_PADDING);
        dest.putLong(val);
    }

    static void insertInt(BigInteger bigGuy, ByteBuffer dest) {
        final byte[] arr = bigGuy.toByteArray();
        final byte paddingByte = bigGuy.signum() == -1 ? NEGATIVE_ONE_BYTE : ZERO_BYTE;
        final int lim = 32 - arr.length;
        for (int i = 0; i < lim; i++) {
            dest.put(paddingByte);
        }
        dest.put(arr);
    }

    static void packInt(long value, int byteLen, ByteBuffer dest) {
        if(value >= 0) {
            dest.position(dest.position() + (byteLen - Integers.len(value)));
            Integers.putLong(value, dest);
        } else {
            final int paddingBytes = byteLen - BizarroIntegers.len(value);
            for (int i = 0; i < paddingBytes; i++) {
                dest.put(Encoding.NEGATIVE_ONE_BYTE);
            }
            BizarroIntegers.putLong(value, dest);
        }
    }

    static void packInt(BigInteger bigGuy, int byteLen, ByteBuffer dest) {
        byte[] arr = bigGuy.toByteArray();
        final int paddingBytes = byteLen - arr.length;
        if(bigGuy.signum() == -1) {
            for (int i = 0; i < paddingBytes; i++) {
                dest.put(Encoding.NEGATIVE_ONE_BYTE);
            }
        } else {
            for (int i = 0; i < paddingBytes; i++) {
                dest.put((byte) 0);
            }
        }
        dest.put(arr);
    }
}
