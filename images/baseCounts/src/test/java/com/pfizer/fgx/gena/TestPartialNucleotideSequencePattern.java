package com.pfizer.fgx.gena;

import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class TestPartialNucleotideSequencePattern {

    @Test
    public void matchFully(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(8)
                .addSequence(Range.ofLength(8), NucleotideSequence.of("ACGTACGT"))
                .build();

        List<Range> actual = sut.findMatches("ACGTACGT")
                .collect(Collectors.toList());

        assertEquals(List.of(Range.ofLength(8)), actual);
    }

    @Test
    public void matchFullyWithOffset(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(500)
                .addSequence(Range.of(200, 207), NucleotideSequence.of("ACGTACGT"))
                .build();

        List<Range> actual = sut.findMatches("ACGTACGT")
                .collect(Collectors.toList());

        assertEquals(List.of(Range.of(200, 207)), actual);
    }

    @Test
    public void matchFullyWithOffsetMultipleMatches(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(500)
                .addSequence(Range.of(200, 207), NucleotideSequence.of("ACGTACGT"))
                .addSequence(Range.of(432, 439), NucleotideSequence.of("ACGTACGT"))
                .build();

        List<Range> actual = sut.findMatches("ACGTACGT")
                .collect(Collectors.toList());

        assertEquals(List.of(Range.of(200, 207), Range.of(432, 439)), actual);
    }
    @Test
    public void matchFullyWithOffsetMultipleMatchesButSubRange(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(500)
                .addSequence(Range.of(200, 207), NucleotideSequence.of("ACGTACGT"))
                .addSequence(Range.of(432, 439), NucleotideSequence.of("ACGTACGT"))
                .build();

        List<Range> actual = sut.findMatches("ACGTACGT", Range.of(400,500))
                .collect(Collectors.toList());

        assertEquals(List.of( Range.of(432, 439)), actual);
    }
    @Test
    public void matchFullyWithOffsetMultipleSequencesButOnlyOneMatch(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(500)
                .addSequence(Range.of(200, 207), NucleotideSequence.of("ACGTACGT"))
                .addSequence(Range.of(432, 439), NucleotideSequence.of("AAAAAAAA"))
                .build();

        List<Range> actual = sut.findMatches("ACGTACGT")
                .collect(Collectors.toList());

        assertEquals(List.of(Range.of(200, 207)), actual);
    }
    @Test
    public void multiMatch(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(8)
                .addSequence(Range.ofLength(8), NucleotideSequence.of("ACGTACGT"))
                .build();

        List<Range> actual = sut.findMatches("ACGT")
                .collect(Collectors.toList());

        assertEquals(List.of(Range.of(0,3), Range.of(4,7)), actual);
    }

    @Test
    public void multiMatchWithOffset(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(500)
                .addSequence(Range.of(200,207), NucleotideSequence.of("ACGTACGT"))
                .build();

        List<Range> actual = sut.findMatches("ACGT")
                .collect(Collectors.toList());

        assertEquals(List.of(Range.of(200,203), Range.of(204,207)), actual);
    }

    @Test
    public void doesNotMatch(){
        PartialNucleotideSequence sut = new PartialNucleotideSequence.Builder(8)
                .addSequence(Range.ofLength(8), NucleotideSequence.of("ACGTACGT"))
                .build();

        List<Range> actual = sut.findMatches("CCCGGTGTGT")
                .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), actual);
    }
}
