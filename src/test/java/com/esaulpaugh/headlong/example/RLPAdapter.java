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
package com.esaulpaugh.headlong.example;

import com.esaulpaugh.headlong.rlp.exception.DecodeException;

public interface RLPAdapter<T> {

    // default interface methods not supported on Android except Android N+
//    default T decode(byte[] rlp) throws DecodeException {
//        return decode(rlp, 0);
//    }

    T decode(byte[] rlp, int index) throws DecodeException;

    byte[] encode(T t);

}
