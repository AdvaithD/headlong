package com.esaulpaugh.headlong.abi;

import com.esaulpaugh.headlong.abi.util.ClassNames;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * Unsigned 0 or 1.
 */
class BooleanType extends UnitType<Boolean> {

    static final BooleanType INSTANCE = new BooleanType();

    static final String CLASS_NAME = Boolean.class.getName();
    static final String ARRAY_CLASS_NAME_STUB = ClassNames.getArrayClassNameStub(boolean[].class);

    private BooleanType() {
        super("bool", 1, true);
    }

    @Override
    public String className() {
        return CLASS_NAME;
    }

    @Override
    String arrayClassNameStub() {
        return ARRAY_CLASS_NAME_STUB;
    }

    @Override
    int typeCode() {
        return TYPE_CODE_BOOLEAN;
    }

    @Override
    int byteLengthPacked(Object value) {
        return 1;
    }

    @Override
    public Boolean parseArgument(String s) {
        Boolean bool = Boolean.parseBoolean(s);
        validate(bool);
        return bool;
    }

    @Override
    Boolean decode(ByteBuffer bb, byte[] unitBuffer) {
        bb.get(unitBuffer, 0, UNIT_LENGTH_BYTES);
        BigInteger bi = new BigInteger(unitBuffer);
        validateBigIntBitLen(bi);
        switch (bi.byteValue()) {
        case 0: return Boolean.FALSE;
        case 1: return Boolean.TRUE;
        default: throw new IllegalArgumentException("negative value for boolean type");
        }
    }
}
