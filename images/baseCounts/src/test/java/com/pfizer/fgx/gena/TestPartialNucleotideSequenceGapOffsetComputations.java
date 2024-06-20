package com.pfizer.fgx.gena;

import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
public class TestPartialNucleotideSequenceGapOffsetComputations {

    @Test
    public void noGapsOneSequence(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(100)
                .addSequence(Range.of(50,57), NucleotideSequence.of("ACGTACGT"))
                .build();

        assertEquals(Collections.emptyList(), sut.getGapOffsets());
        assertEquals(100, sut.getLength());
        assertEquals(100, sut.getUngappedLength());
        for(int i=0; i< sut.getLength(); i++){
            assertFalse(sut.isGap(i));
            assertEquals(0, sut.getNumberOfGapsUntil(i));
            assertEquals(i, sut.getGappedOffsetFor(i));
            assertEquals(i, sut.getUngappedOffsetFor(i));
        }


    }

    @Test
    public void oneGapOneSequence(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(100)
                .addSequence(Range.of(50,57), NucleotideSequence.of("ACGT-CGT"))
                .build();

        assertEquals(List.of(54), sut.getGapOffsets());
        assertArrayEquals(new int[]{54}, sut.gaps().toArray());
        assertEquals(100, sut.getLength());
        assertEquals(99, sut.getUngappedLength());
        assertEquals(1, sut.getNumberOfGaps());

        for(int i=0; i< 54; i++){

            assertFalse(sut.isGap(i));
            assertEquals(""+i, 0, sut.getNumberOfGapsUntil(i));
            assertEquals(""+i, i, sut.getGappedOffsetFor(i));
            assertEquals(i, sut.getUngappedOffsetFor(i));
        }

        assertTrue(sut.isGap(54));
        assertEquals(55, sut.getGappedOffsetFor(54));
        assertEquals(53, sut.getUngappedOffsetFor(54));
        assertEquals( 1, sut.getNumberOfGapsUntil(54));

        for(int i=55; i< 100; i++){

            assertFalse(sut.isGap(i));
            assertEquals(""+i, 1, sut.getNumberOfGapsUntil(i));
            assertEquals(i+1, sut.getGappedOffsetFor(i));
            assertEquals(i-1, sut.getUngappedOffsetFor(i));
        }


    }

    @Test
    public void noGapsMultipleSequences(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(100)
                .addSequence(Range.of(50,57), NucleotideSequence.of("ACGTACGT"))

                .addSequence(Range.of(70,77), NucleotideSequence.of("ACGTACGT"))
                .build();

        assertEquals(Collections.emptyList(), sut.getGapOffsets());
        assertEquals(100, sut.getLength());
        assertEquals(100, sut.getUngappedLength());
        for(int i=0; i< sut.getLength(); i++){
            assertFalse(sut.isGap(i));
            assertEquals(0, sut.getNumberOfGapsUntil(i));
            assertEquals(i, sut.getGappedOffsetFor(i));
            assertEquals(i, sut.getUngappedOffsetFor(i));
        }


    }
}
