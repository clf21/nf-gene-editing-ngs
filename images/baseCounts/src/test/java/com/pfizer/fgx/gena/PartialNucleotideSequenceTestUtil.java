package com.pfizer.fgx.gena;

import org.jcvi.jillion.core.residue.nt.NucleotideSequenceBuilder;

import static org.junit.Assert.assertEquals;

public final class PartialNucleotideSequenceTestUtil {

    private PartialNucleotideSequenceTestUtil(){
        //can not instantiate
    }

    public static void assertSequenceEquals(String expectedSequence, PartialNucleotideSequence.PartialNucleotideSequenceBuilder actual){
        assertEquals(expectedSequence,new NucleotideSequenceBuilder(Math.max(1,(int)actual.getLength()))
                .append(actual)
                .toString());
    }
}
