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

import com.esaulpaugh.headlong.rlp.exception.DecodeException;
import com.esaulpaugh.headlong.rlp.util.FloatingPoint;
import com.esaulpaugh.headlong.rlp.util.Integers;
import com.esaulpaugh.headlong.util.Strings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RLPEncoderTest {

    @Test
    public void encodeSequentially() {
        Object[] objects = new Object[] {
                new Object[0],
                new byte[0]
        };
        assertArrayEquals(
                new byte[] { (byte)0xc0, (byte)0x80 },
                RLPEncoder.encodeSequentially(objects)
        );
    }

    @Test
    public void encodeAsList() {
        Object[] objects = new Object[] {
                new Object[0],
                new byte[0]
        };
        assertArrayEquals(
                new byte[] { (byte)0xc2, (byte)0xc0, (byte)0x80 },
                RLPEncoder.encodeAsList(objects)
        );
    }

    @Test
    public void toList() throws DecodeException {

        RLPItem item0 = RLPDecoder.RLP_STRICT.wrap(new byte[] {(byte) 0x81, (byte) 0x80 });
        RLPItem item1 = RLPDecoder.RLP_STRICT.wrap(new byte[] {(byte) 0x7e });
        RLPItem item2 = RLPDecoder.RLP_STRICT.wrap(new byte[] {(byte) 0xc1, (byte) 0x80 });

        RLPList rlpList = RLPEncoder.toList(item0, item1, item2);
        List<RLPItem> elements = rlpList.elements(RLPDecoder.RLP_STRICT);

        assertEquals(3, elements.size());

        assertNotSame(elements.get(0), item0);
        assertNotSame(elements.get(1), item1);
        assertNotSame(elements.get(2), item2);

        assertEquals(elements.get(0), item0);
        assertEquals(elements.get(1), item1);
        assertEquals(elements.get(2), item2);
    }

    @Test
    public void testDatatypes() throws DecodeException {

        char c = '\u0009';
        String str = "7 =IIii$%&#*~\t\n\b";
        byte by = -1;
        short sh = Short.MIN_VALUE;

        int i = 95223;
        long l = 9864568923852L;
        BigInteger bi = BigInteger.valueOf(l * -1001);

        float f = 0.91254f;
        double d = 133.9185523d;
        BigDecimal bd = new BigDecimal(BigInteger.ONE, 18);

        byte[] rlp = RLPEncoder.encodeSequentially(
                Integers.toBytes((short) c),
                str.getBytes(StandardCharsets.UTF_8),
                Integers.toBytes(by),
                Integers.toBytes(sh),
                Integers.toBytes(i),
                Integers.toBytes(l),
                bi.toByteArray(),
                FloatingPoint.toBytes(f),
                FloatingPoint.toBytes(d),
                bd.unscaledValue().toByteArray()
        );

        RLPIterator iter = RLPDecoder.RLP_STRICT.sequenceIterator(rlp);

        assertEquals(iter.next().asChar(), c);
        assertEquals(iter.next().asString(Strings.UTF_8), str);
        assertEquals(iter.next().asByte(), by);
        assertEquals(iter.next().asShort(), sh);

        assertEquals(iter.next().asInt(), i);
        assertEquals(iter.next().asLong(), l);
        assertEquals(iter.next().asBigInt(), bi);

        assertEquals(iter.next().asFloat(), f, 0.0d);
        assertEquals(iter.next().asDouble(), d, 0.0d);
        assertEquals(iter.next().asBigDecimal(bd.scale()), bd);
    }
}
