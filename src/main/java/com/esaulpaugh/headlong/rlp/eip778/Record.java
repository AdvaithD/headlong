package com.esaulpaugh.headlong.rlp.eip778;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.rlp.RLPList;
import com.esaulpaugh.headlong.rlp.RLPListIterator;
import com.esaulpaugh.headlong.rlp.exception.DecodeException;
import com.esaulpaugh.headlong.util.Strings;

import java.util.Arrays;

import static com.esaulpaugh.headlong.rlp.RLPDecoder.RLP_STRICT;
import static com.esaulpaugh.headlong.util.Strings.HEX;
import static com.esaulpaugh.headlong.util.Strings.UTF_8;

/**
 * Implementation of EIP 778: Ethereum Node Records (ENR), https://eips.ethereum.org/EIPS/eip-778
 */
public class Record {

    private static final int MAX_RECORD_LEN = 300;

    private final byte[] record;

    public Record(long seq, KeyValuePair[] pairs, Signer signer) {
        final int signatureLen = signer.signatureLength();
        final int signatureItemLen = RLPEncoder.prefixLength(signatureLen) + signatureLen;
        final long payloadLenLong = RLPEncoder.encodedLen(seq) + RLPEncoder.dataLen(pairs);
        final long recordListPayloadLenLong = signatureItemLen + payloadLenLong;
        final int recordPrefixLen = RLPEncoder.prefixLength(recordListPayloadLenLong);
        final long recordLenLong = recordPrefixLen + recordListPayloadLenLong;
        if(recordLenLong > MAX_RECORD_LEN) {
            throw new IllegalArgumentException("record length exceeds maximum: " + recordLenLong + " > " + MAX_RECORD_LEN);
        }

        final int recordLen = (int) recordLenLong;
        byte[] record = new byte[recordLen];
        RLPEncoder.insertListPrefix((int) recordListPayloadLenLong, record, 0);
        int contentListOffset = recordPrefixLen + signatureItemLen - RLPEncoder.prefixLength(payloadLenLong);
        RLPEncoder.insertRecordContentList((int) payloadLenLong, seq, pairs, record, contentListOffset);
        byte[] signature = signer.sign(record, contentListOffset, recordLen - contentListOffset);
        RLPEncoder.insertRecordSignature(signature, record, recordPrefixLen);

        this.record = record;
    }

    public Record(byte[] record) {
        this.record = Arrays.copyOf(record, record.length);
    }

    RLPList getRecord() throws DecodeException {
        return RLP_STRICT.wrapList(record);
    }

    public RLPItem getSignature() throws DecodeException {
        return RLP_STRICT.wrapList(record, 0).iterator(RLP_STRICT).next();
    }

    public RLPList getContent() throws DecodeException {
        return RLP_STRICT.wrapList(getContent(RLP_STRICT.wrapList(record, 0)));
    }

    private byte[] getSignature(RLPList recordList) throws DecodeException {
        return recordList.iterator(RLP_STRICT).next().data();
    }

    private byte[] getContent(RLPList recordList) throws DecodeException {
        int elementsIndex = recordList.iterator(RLP_STRICT).next().endIndex;
        int dataLen = recordList.encodingLength() - elementsIndex;
        byte[] content = new byte[RLPEncoder.prefixLength(dataLen) + dataLen];
        int prefixLen = RLPEncoder.insertListPrefix(dataLen, content, 0);
        System.arraycopy(record, elementsIndex, content, prefixLen, dataLen);
        return content;
    }

    public RLPList decode(Verifier verifier) throws DecodeException {
        RLPList recordList = RLPDecoder.RLP_STRICT.wrapList(record, 0);
        byte[] signature = getSignature(recordList);
        byte[] content = getContent(recordList);
        if(verifier.verify(signature, content)) { // verify content
            return RLPDecoder.RLP_STRICT.wrapList(content);
        }
        return null;
    }

    public interface Signer {
        int signatureLength();
        byte[] sign(byte[] message, int off, int len);
    }

    public interface Verifier {
        boolean verify(byte[] signature, byte[] content) throws DecodeException;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(record);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Record record1 = (Record) o;
        return Arrays.equals(record, record1.record);
    }

    @Override
    public String toString() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("record len = ").append(record.length).append('\n');
            sb.append("record = ").append(Strings.encode(record)).append('\n');

            RLPListIterator iter = RLP_STRICT.listIterator(record);
            sb.append("signature = ").append(iter.next().asString(HEX)).append('\n');
            sb.append("seq = ").append(iter.next().asLong()).append('\n');
            while (iter.hasNext()) {
                sb.append(iter.next().asString(UTF_8)).append(" --> ").append(iter.next().asString(HEX)).append('\n');
            }
            return sb.toString();
        } catch (DecodeException de) {
            throw new RuntimeException(de);
        }
    }
}