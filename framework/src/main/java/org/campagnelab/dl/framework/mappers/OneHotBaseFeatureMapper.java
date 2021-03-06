package org.campagnelab.dl.framework.mappers;

import org.campagnelab.goby.util.WarningCounter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Maps a sequence to a fixed set of integers, which are used to encode each position in one-hot encoding,
 * at a specific baseIndex into the sequence.
 * By default, the set of integers correspond to biological bases-
 * - a/A=0
 * - c/C=1
 * - t/T=2
 * - g/G=3
 * - n/N=4
 * - other=5
 * but other mappings can be specified using the constructor with the recordStringAtBaseToInteger
 * function parameter, which takes in a string representation of a record and a base index,
 * and returns the one-hot encoding integer at that position.
 * <p>
 * Created by rct66 on 10/25/16.
 */
public class OneHotBaseFeatureMapper<RecordType> implements FeatureMapper<RecordType> {
    static private Logger LOG = LoggerFactory.getLogger(OneHotBaseFeatureMapper.class);
    private final int numFeatures;
    private boolean ignoreOutOfRangeIndices = false;
    private Function<RecordType, String> recordToString;
    private BiFunction<String, Integer, Integer> recordStringAtBaseToInteger;
    private int baseIndex;


    /**
     * Constructs a OneHotBaseFeatureMapper that can be used to encode DNA sequences
     *
     * @param baseIndex      base index that this OneHotBaseFeatureMapper looks at
     * @param recordToString function that converts a sequence record into a string
     */
    public OneHotBaseFeatureMapper(int baseIndex, Function<RecordType, String> recordToString) {
        this(baseIndex, recordToString, OneHotBaseFeatureMapper::getIntegerOfBase, 7);
    }

    public void setIgnoreOutOfRangeIndices(boolean ignoreOutOfRangeIndices) {
        this.ignoreOutOfRangeIndices = ignoreOutOfRangeIndices;
    }

    /**
     * Constructs a OneHotBaseFeatureMapper that can be used to encode arbitrary sequences
     *
     * @param baseIndex                   base index that this OneHotBaseFeatureMapper looks at
     * @param recordToString              function that converts a sequence record into a string
     * @param recordStringAtBaseToInteger function that takes in a record string (i.e., that
     *                                    converted by recordToString) and an integer representing
     *                                    a base index, and returns a one-hot encoding integer
     * @param numFeatures                 number of possible features returned by recordStringAtBaseToInteger
     */
    public OneHotBaseFeatureMapper(int baseIndex, Function<RecordType, String> recordToString,
                                   BiFunction<String, Integer, Integer> recordStringAtBaseToInteger,
                                   int numFeatures) {
        this.baseIndex = baseIndex;
        this.recordToString = recordToString;
        this.recordStringAtBaseToInteger = recordStringAtBaseToInteger;
        this.numFeatures = numFeatures;

    }

    private static final int[] indices = new int[]{0, 0};

    public int numberOfFeatures() {
        return numFeatures;
    }

    private String cachedString;

    @Override
    public void prepareToNormalize(RecordType record, int indexOfRecord) {
        cachedString = recordToString.apply(record);
    }

    @Override
    public void mapFeatures(RecordType record, INDArray inputs, int indexOfRecord) {
        indices[0] = indexOfRecord;
        for (int featureIndex = 0; featureIndex < numberOfFeatures(); featureIndex++) {
            indices[1] = featureIndex;
            inputs.putScalar(indices, produceFeature(record, featureIndex));
        }
    }

    @Override
    public float produceFeature(RecordType record, int featureIndex) {
        if (baseIndex >= cachedString.length()) {
            if (!ignoreOutOfRangeIndices) {
                counter.warn(LOG, String.format("incompatible character index: %d for context: %s of length %d",
                        baseIndex, cachedString, cachedString.length()));
            }
            return 0;
        } else {
            int value = recordStringAtBaseToInteger.apply(cachedString, baseIndex);
            return value == featureIndex ? 1F : 0F;
        }

    }

    @Override
    public boolean hasMask() {
        return false;
    }

    @Override
    public MappedDimensions dimensions() {
        return new MappedDimensions(numberOfFeatures());
    }

    @Override
    public void maskFeatures(RecordType record, INDArray mask, int indexOfRecord) {
    }

    @Override
    public boolean isMasked(RecordType record, int featureIndex) {
        return false;
    }

    private static WarningCounter counter = new WarningCounter();

    public static int getIntegerOfBase(String context, int baseIndex) {
        if (baseIndex < 0 || baseIndex >= context.length()) {

            return 0;
        }
        Character base = context.charAt(baseIndex);
        int baseInt;
        switch (base) {
            case 'a':
            case 'A':
                baseInt = 1;
                break;
            case 't':
            case 'T':
                baseInt = 2;
                break;
            case 'c':
            case 'C':
                baseInt = 3;
                break;
            case 'g':
            case 'G':
                baseInt = 4;
                break;
            case 'n':
            case 'N':
                baseInt = 5;
                break;
            case '-':
                baseInt = 6;
                break;
            default:
                baseInt = 0;
                break;
        }
        return baseInt;
    }
}
