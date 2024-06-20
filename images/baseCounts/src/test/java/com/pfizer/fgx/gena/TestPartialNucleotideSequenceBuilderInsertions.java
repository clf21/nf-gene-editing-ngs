package com.pfizer.fgx.gena;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.jcvi.jillion.core.residue.nt.NucleotideSequenceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Parameterized tests to test insertions using each of the different
 * insertion methods (string, array, other builder etc) so we only have to write
 * the test once and it will try each method to make sure each one
 * is implemented correctly.
 */
@RunWith(Parameterized.class)
public class TestPartialNucleotideSequenceBuilderInsertions {


    @Parameterized.Parameters
    public static List<Object[]> data(){
        return List.of(
                //single insertion
                new Object[]{"ACGTACGT", ops().insert( 2, "-"), 9,8 ,"AC-GTACGT"},
                new Object[]{"ACGTACGT", ops().insert(6, "-"), 9,8 ,"ACGTAC-GT"},
                new Object[]{"ACGTACGT", ops().insert(6, "T"), 9,9 ,"ACGTACTGT"},
            //single insertion of multiple bases
                new Object[]{"ACGTACGT", ops().insert(2, "--"), 10,8 ,"AC--GTACGT"},
                new Object[]{"ACGTACGT", ops().insert(6, "--"), 10,8 ,"ACGTAC--GT"},
                new Object[]{"ACGTACGT", ops().insert(6, "AA"), 10,10 ,"ACGTACAAGT"},
                //multiple insertions different location
                new Object[]{"ACGTACGT", ops().insert(2, "-")
                                                .insert(4, "-"), 10,8 ,"AC-G-TACGT"},
                new Object[]{"ACGTACGT", ops().insert(6, "-")
                                            .insert(4,"-"), 10,8 ,"ACGT-AC-GT"},

                //multiple insertions same location
                new Object[]{"ACGTACGT", ops().insert(2, "-")
                                              .insert(2, "-"), 10,8 ,"AC--GTACGT"},

                //multiple islands of partial sequence
                new Object[]{"ACGTACGTNNNNNACGTACGT", ops().insert( 2, "-"), 22,21 ,"AC-GTACGTNNNNNACGTACGT"},
                new Object[]{"ACGTACGTNNNNNACGTACGT", ops().insert( 2, "-")
                                                            .insert(20, "-"), 23,21 ,"AC-GTACGTNNNNNACGTAC-GT"},

        new Object[]{"ACGTACGTNNNNNACGTACGT", ops().insert( 10, "-"), 22,21 ,"ACGTACGTNN-NNNACGTACGT"},
                new Object[]{"ACGTACGTNNNNNACGTACGT", ops().insert( 10, "-")
                        .insert(20, "-"), 23,21 ,"ACGTACGTNN-NNNACGTAC-GT"}


        );
    }

    public static class InsertionOperations{
        private final ListMultimap<Integer, String> insertions=MultimapBuilder.linkedHashKeys().arrayListValues().build();

        public InsertionOperations insert(int offset, String seq){
            insertions.put(offset, seq);
            return this;
        }
    }


    public static InsertionOperations ops(){
        return new InsertionOperations();
    }

    private String originalSequence;
    private int newLength;
    private int newUngappedLength;

    private InsertionOperations ops;
    private String expectedSequence;
    public TestPartialNucleotideSequenceBuilderInsertions(String originalSequence, InsertionOperations ops,
                                                          int newLength, int newUngappedLength,
                                                          String expectedSequence){
        this.originalSequence = originalSequence;
        this.newLength = newLength;
        this.newUngappedLength = newUngappedLength;
        this.ops = ops;
        this.expectedSequence = expectedSequence;
    }

    PartialNucleotideSequence.PartialNucleotideSequenceBuilder sut;

    @Before
    public void setup(){

        sut = createPartialSeqBuilderFor(originalSequence);


    }
    private static final Pattern NS_PATTER = Pattern.compile("N+");
    private static PartialNucleotideSequence.PartialNucleotideSequenceBuilder createPartialSeqBuilderFor(String seq) {


        PartialNucleotideSequence.Builder builder = PartialNucleotideSequence.builder(seq.length());
        if(seq.contains("N")) {
            int start=0;
            Matcher matcher = NS_PATTER.matcher(seq);
            while (matcher.find()) {
                builder.addSequence(Range.of(start, matcher.start()-1), NucleotideSequence.of(seq.substring(start, matcher.start())));
                start= matcher.end();
            }
            //add last if any
            if(start< seq.length()) {
                builder.addSequence(Range.of(start, seq.length()-1), NucleotideSequence.of(seq.substring(start)));
            }
        }else{
            builder.addSequence(Range.ofLength(seq.length()), NucleotideSequence.of(seq));
        }
        return builder.build()
                .toBuilder();
    }

    @Test
    public void string(){
        ops.insertions.forEach((offset, seq)->{
            sut.insert(offset, seq);
        });

        PartialNucleotideSequenceTestUtil.assertSequenceEquals(expectedSequence, sut);
        assertEquals(newLength, sut.getLength());
        assertEquals(newUngappedLength, sut.getUngappedLength());
    }
    @Test
    public void charArray(){
        ops.insertions.forEach((offset, seq)->{
            sut.insert(offset, seq.toCharArray());
        });

        PartialNucleotideSequenceTestUtil.assertSequenceEquals(expectedSequence, sut);
        assertEquals(newLength, sut.getLength());
        assertEquals(newUngappedLength, sut.getUngappedLength());
    }
    @Test
    public void nucleotideSequenceBuilder(){
        ops.insertions.forEach((offset, seq)->{
            sut.insert(offset, new NucleotideSequenceBuilder(seq));
        });

        PartialNucleotideSequenceTestUtil.assertSequenceEquals(expectedSequence, sut);
        assertEquals(newLength, sut.getLength());
        assertEquals(newUngappedLength, sut.getUngappedLength());
    }

    @Test
    public void nucleotideSequence(){
        ops.insertions.forEach((offset, seq)->{
            sut.insert(offset,  NucleotideSequence.of(seq));
        });

        PartialNucleotideSequenceTestUtil.assertSequenceEquals(expectedSequence, sut);
        assertEquals(newLength, sut.getLength());
        assertEquals(newUngappedLength, sut.getUngappedLength());
    }
    @Test
    public void nucleotideArray(){
        ops.insertions.forEach((offset, seq)->{
            sut.insert(offset,  new NucleotideSequenceBuilder(seq).toArray());
        });

        PartialNucleotideSequenceTestUtil.assertSequenceEquals(expectedSequence, sut);
        assertEquals(newLength, sut.getLength());
        assertEquals(newUngappedLength, sut.getUngappedLength());
    }

    @Test
    public void otherPartialBuilder(){
        ops.insertions.forEach((offset, seq)->{
            sut.insert(offset, createPartialSeqBuilderFor(seq));
        });

        PartialNucleotideSequenceTestUtil.assertSequenceEquals(expectedSequence, sut);
        assertEquals(newLength, sut.getLength());
        assertEquals(newUngappedLength, sut.getUngappedLength());
    }
}
