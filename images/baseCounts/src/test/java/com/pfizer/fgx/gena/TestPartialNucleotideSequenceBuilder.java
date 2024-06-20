package com.pfizer.fgx.gena;

import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.jcvi.jillion.core.residue.nt.NucleotideSequenceBuilder;
import org.junit.Test;

import static org.junit.Assert.*;
public class TestPartialNucleotideSequenceBuilder {

    @Test
    public void noChangesShouldMakeAllNs(){
        PartialNucleotideSequence.PartialNucleotideSequenceBuilder sut =  PartialNucleotideSequence.builder(10)
                .addSequence(Range.ofLength(10), NucleotideSequence.of("NNNNNNNNNN"))
                .build()
                .toBuilder();

        PartialNucleotideSequenceTestUtil.assertSequenceEquals("NNNNNNNNNN", sut);


    }

    @Test
    public void emptyBuilderIsEmpty(){
        PartialNucleotideSequence.PartialNucleotideSequenceBuilder sut =  PartialNucleotideSequence.builder(10)
                .addSequence(Range.ofLength(10), NucleotideSequence.of("NNNNNNNNNN"))
                .build()
                .newEmptyBuilder();

        assertEquals(0, sut.getLength());
        assertEquals(0, sut.getUngappedLength());
        PartialNucleotideSequenceTestUtil.assertSequenceEquals("", sut);
    }



    @Test
    public void oneSequenceFullLengthNoChanges(){
        PartialNucleotideSequence.PartialNucleotideSequenceBuilder sut =  PartialNucleotideSequence.builder(8)
                .addSequence(Range.ofLength(8), NucleotideSequence.of("ACGTACGT"))
                .build()
                .toBuilder();

        PartialNucleotideSequenceTestUtil.assertSequenceEquals("ACGTACGT", sut);


    }
    @Test
    public void oneSequenceNotFullLengthNoChanges(){
        PartialNucleotideSequence.PartialNucleotideSequenceBuilder sut =  PartialNucleotideSequence.builder(10)
                .addSequence(Range.ofLength(8), NucleotideSequence.of("ACGTACGT"))
                .build()
                .toBuilder();

        PartialNucleotideSequenceTestUtil.assertSequenceEquals("ACGTACGTNN", sut);


    }
    @Test
    public void oneSequenceNotFullLengthOtherSideNoChanges(){
        PartialNucleotideSequence.PartialNucleotideSequenceBuilder sut =  PartialNucleotideSequence.builder(10)
                .addSequence(Range.of(2,9), NucleotideSequence.of("ACGTACGT"))
                .build()
                .toBuilder();

        PartialNucleotideSequenceTestUtil.assertSequenceEquals("NNACGTACGT", sut);
    }
    @Test
    public void oneInsertionCharArray(){
        PartialNucleotideSequence.PartialNucleotideSequenceBuilder sut =  PartialNucleotideSequence.builder(8)
                .addSequence(Range.ofLength(8), NucleotideSequence.of("ACGTACGT"))
                .build()
                .toBuilder();

        sut.insert(4, new char[]{'-'});
        assertEquals(9, sut.getLength());
        assertEquals(8, sut.getUngappedLength());
        PartialNucleotideSequenceTestUtil.assertSequenceEquals("ACGT-ACGT", sut);
    }


}
