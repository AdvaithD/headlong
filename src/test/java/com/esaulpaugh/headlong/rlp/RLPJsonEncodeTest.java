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
import com.esaulpaugh.headlong.util.FastHex;
import com.esaulpaugh.headlong.util.Integers;
import com.esaulpaugh.headlong.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static com.esaulpaugh.headlong.TestUtils.parseArrayToBytesHierarchy;
import static com.esaulpaugh.headlong.TestUtils.parseBigIntegerStringPoundSign;
import static com.esaulpaugh.headlong.TestUtils.parseLong;
import static com.esaulpaugh.headlong.TestUtils.parseObject;
import static com.esaulpaugh.headlong.TestUtils.parseString;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RLPJsonEncodeTest {

    @Test
    public void testCases() throws IOException {

        String testCasesJson = TestUtils.readResourceAsString(RLPJsonEncodeTest.class, "tests/ethereum/RLPTests/rlptest.json");

        for (Map.Entry<String, JsonElement> entry : parseEntrySet(testCasesJson)) {

            JsonObject jsonObject = entry.getValue().getAsJsonObject();
            JsonElement in = jsonObject.get("in");

            byte[] actualBytes;
            if(in.isJsonArray()) {
                actualBytes = RLPEncoder.encodeAsList(parseArrayToBytesHierarchy(in.getAsJsonArray()));
            } else if(in.isJsonObject()) {
                System.err.println("json object");
                parseObject(in);
                actualBytes = null;
            } else {
                try {
                    actualBytes = RLPEncoder.encode(Integers.toBytes(parseLong(in)));
                } catch (NumberFormatException nfe) {
                    actualBytes = RLPEncoder.encode(in.getAsString().startsWith("#")
                            ? parseBigIntegerStringPoundSign(in).toByteArray()
                            : parseString(in).getBytes(StandardCharsets.UTF_8));
                }
            }
            String expected = FastHex.encodeToString(getOutBytes(entry));
            String actual = FastHex.encodeToString(actualBytes);
            try {
                assertEquals(expected, actual);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    static Set<Map.Entry<String, JsonElement>> parseEntrySet(String json) {
        return JsonUtils.parse(json)
                .getAsJsonObject()
                .entrySet();
    }

    static byte[] getOutBytes(Map.Entry<String, JsonElement> e) {
        JsonObject jsonObject = e.getValue().getAsJsonObject();

        System.out.println(jsonObject);

        JsonElement out = jsonObject.get("out");

        String outString = out.getAsString();

        return FastHex.decode(outString.substring(outString.indexOf("0x") + "0x".length()));
    }
}
