package com.pfizer.fgx.gena;

import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.nt.INucleotideSequence;
import org.jcvi.jillion.core.residue.nt.Nucleotide;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.*;
public class TestPartialNucleotideSequence {

    @Test
    public void noSequenceShouldBeAllNs(){
        Iterator<Nucleotide> iter = PartialNucleotideSequence.builder(1234).build().iterator();
        for(int i=0; i<1234; i++){
            assertTrue(iter.hasNext());
            assertEquals(Nucleotide.Unknown, iter.next());
        }
        assertFalse(iter.hasNext());
        assertThrows(NoSuchElementException.class, iter::next);
    }

    @Test(expected = IllegalArgumentException.class)
    public void overlappingMismatchedSequenceSameRangeShouldErrorOut(){
        PartialNucleotideSequence.builder(1234)
                .addSequence(Range.of(0,4), NucleotideSequence.of("ACGT"))
                .addSequence(Range.of(0,4), NucleotideSequence.of("ACTT"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void overlappingMismatchedSequenceOverlappingRangeBeforeShouldErrorOut(){
        PartialNucleotideSequence.builder(1234)
                .addSequence(Range.of(2,6), NucleotideSequence.of(  "CCTT"))
                .addSequence(Range.of(0,4), NucleotideSequence.of("ACGT"));

    }
    @Test(expected = IllegalArgumentException.class)
    public void overlappingMismatchedSequenceOverlappingRangeAfterShouldErrorOut(){
        PartialNucleotideSequence.builder(1234)
                .addSequence(Range.of(0,4), NucleotideSequence.of("ACGT"))
                .addSequence(Range.of(2,6), NucleotideSequence.of(  "CCTT"));
    }

    @Test
    public void fullSequenceNoGaps(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(8)
                .addSequence(Range.of(0,7), NucleotideSequence.of("ACGTACGT"))
                .build();

        assertEquals(8, seq.getLength());
        assertEquals(8, seq.getUngappedLength());
        sequenceMatches("ACGTACGT", seq);
        sequenceMatches("GTACG", seq.trim(Range.of(2,6)));
        sequenceMatches("GTACG", seq, Range.of(2,6));
        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }

    @Test
    public void fullSequenceAs2PiecesNoOverlapsNoGaps(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(8)
                .addSequence(Range.of(0,3), NucleotideSequence.of("ACGT"))
                .addSequence(Range.of(4,7), NucleotideSequence.of(    "ACGT"))
                .build();

        assertEquals(8, seq.getLength());
        assertEquals(8, seq.getUngappedLength());
        sequenceMatches("ACGTACGT", seq);
        sequenceMatches("GTACG", seq.trim(Range.of(2,6)));
        sequenceMatches("GTACG", seq, Range.of(2,6));
        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }

    //TODO handle overlapping sequences

    @Test
    public void fullSequenceAs2PiecesWithOverlapNoGapsBefore(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(8)
                .addSequence(Range.of(0,3), NucleotideSequence.of("ACGT"))
                .addSequence(Range.of(2,7), NucleotideSequence.of(  "GTACGT"))
                .build();

        assertEquals(8, seq.getLength());
        assertEquals(8, seq.getUngappedLength());
        sequenceMatches("ACGTACGT", seq);
        sequenceMatches("GTACG", seq.trim(Range.of(2,6)));
        sequenceMatches("GTACG", seq, Range.of(2,6));
        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }
    @Test
    public void fullSequenceAs2PiecesWithOverlapNoGapsAfter(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(8)

                .addSequence(Range.of(2,7), NucleotideSequence.of(  "GTACGT"))
                .addSequence(Range.of(0,3), NucleotideSequence.of("ACGT"))
                .build();

        assertEquals(8, seq.getLength());
        assertEquals(8, seq.getUngappedLength());
        sequenceMatches("ACGTACGT", seq);
        sequenceMatches("GTACG", seq.trim(Range.of(2,6)));
        sequenceMatches("GTACG", seq, Range.of(2,6));
        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }
    @Test
    public void fullSequenceAs2PiecesWithOverlapNoGapsCompletelyInsideExisting(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(8)

                .addSequence(Range.of(0,7), NucleotideSequence.of(  "ACGTACGT"))
                .addSequence(Range.of(0,3), NucleotideSequence.of("ACGT"))
                .build();

        assertEquals(8, seq.getLength());
        assertEquals(8, seq.getUngappedLength());
        sequenceMatches("ACGTACGT", seq);
        sequenceMatches("GTACG", seq.trim(Range.of(2,6)));
        sequenceMatches("GTACG", seq, Range.of(2,6));
        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }

    @Test
    public void partialSequenceSingleNsAtEnd(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(10)
                .addSequence(Range.of(0,7), NucleotideSequence.of("ACGTACGT"))
                .build();

        assertEquals(10, seq.getLength());
        assertEquals(10, seq.getUngappedLength());
        sequenceMatches("ACGTACGTNN", seq);
        sequenceMatches("GTACG", seq.trim(Range.of(2,6)));
        sequenceMatches("GTACG", seq, Range.of(2,6));

        sequenceMatches("GTACGTNN", seq.trim(Range.of(2,10)));
        sequenceMatches("GTACGTNN", seq, Range.of(2,10));
        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }
    @Test
    public void partialSequenceSingleNsAtBeginningAndEnd(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(10)
                .addSequence(Range.of(2,7), NucleotideSequence.of("GTACGT"))
                .build();

        assertEquals(10, seq.getLength());
        assertEquals(10, seq.getUngappedLength());
        sequenceMatches("NNGTACGTNN", seq);
        sequenceMatches("GTACG", seq.trim(Range.of(2,6)));
        sequenceMatches("GTACG", seq, Range.of(2,6));

        sequenceMatches("GTACGTNN", seq.trim(Range.of(2,10)));
        sequenceMatches("GTACGTNN", seq, Range.of(2,10));

        sequenceMatches("NNGTAC", seq.trim(Range.of(0,5)));
        sequenceMatches("NNGTAC", seq, Range.of(0,5));

        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }

    @Test
    public void partialSequenceSingleNsAtBeginningAndEndWithGaps(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(10)
                .addSequence(Range.of(2,7), NucleotideSequence.of("GT-CGT"))
                .build();

        assertEquals(10, seq.getLength());
        assertEquals(9, seq.getUngappedLength());
        sequenceMatches("NNGT-CGTNN", seq);
        sequenceMatches("GT-CG", seq.trim(Range.of(2,6)));
        sequenceMatches("GT-CG", seq, Range.of(2,6));

        sequenceMatches("GT-CGTNN", seq.trim(Range.of(2,10)));
        sequenceMatches("GT-CGTNN", seq, Range.of(2,10));

        sequenceMatches("NNGT-C", seq.trim(Range.of(0,5)));
        sequenceMatches("NNGT-C", seq, Range.of(0,5));

        assertEquals(1, seq.getNumberOfGaps());
        assertEquals(List.of(4), seq.getGapOffsets());
    }

    @Test
    public void partialSequenceSingleNsAtBeginning(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(10)
                .addSequence(Range.of(2,9), NucleotideSequence.of("ACGTACGT"))
                .build();

        assertEquals(10, seq.getLength());
        assertEquals(10, seq.getUngappedLength());
        sequenceMatches("NNACGTACGT", seq);
        sequenceMatches("ACGTA", seq.trim(Range.of(2,6)));
        sequenceMatches("ACGTA", seq, Range.of(2,6));

        sequenceMatches("NNACGTA", seq.trim(Range.of(0,6)));
        sequenceMatches("NNACGTA", seq, Range.of(0,6));
        assertEquals(0, seq.getNumberOfGaps());
        assertEquals(Collections.emptyList(), seq.getGapOffsets());
    }

    @Test
    public void fullSequenceWithGaps(){
        PartialNucleotideSequence seq = PartialNucleotideSequence.builder(8)
                .addSequence(Range.of(0,7), NucleotideSequence.of("ACG-ACGT"))
                .build();

        assertEquals(8, seq.getLength());
        assertEquals(7, seq.getUngappedLength());
        sequenceMatches("ACG-ACGT", seq);
        sequenceMatches("G-ACG", seq.trim(Range.of(2,6)));
        sequenceMatches("G-ACG", seq, Range.of(2,6));
        assertEquals(1, seq.getNumberOfGaps());
        assertEquals(List.of(3), seq.getGapOffsets());
    }

    private static void sequenceMatches(String expected, INucleotideSequence<?,?> seq, Range range){
        Iterator<Nucleotide> iter = seq.iterator(range);
        char[] array = expected.toCharArray();
        for(int i=0; i< array.length; i++){
            char actual = iter.next().getCharacter().charValue();
            assertEquals(i + "  " + array[i], array[i], actual);
        }
    }
    private static void sequenceMatches(String expected, INucleotideSequence<?,?> seq){
        Iterator< Nucleotide> iter = seq.iterator();
        char[] array = expected.toCharArray();
        for(int i=0; i< expected.length(); i++){

                char actual = iter.next().getCharacter().charValue();

                assertEquals(i + "  " + array[i], array[i], actual);

        }
    }
}
