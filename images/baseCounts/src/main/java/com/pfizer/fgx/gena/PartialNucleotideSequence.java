package com.pfizer.fgx.gena;

import gov.nih.ncats.common.stream.StreamUtil;
import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.Rangeable;
import org.jcvi.jillion.core.Ranges;
import org.jcvi.jillion.core.residue.nt.*;
import org.jcvi.jillion.core.util.RangeMap;
import org.jcvi.jillion.core.util.SingleThreadAdder;
import org.jcvi.jillion.core.util.iter.IteratorUtil;
import org.jcvi.jillion.core.util.iter.PeekableIterator;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PartialNucleotideSequence implements INucleotideSequence<PartialNucleotideSequence, PartialNucleotideSequence.PartialNucleotideSequenceBuilder>, MatchableSequence {


    public static class Builder{
        RangeMap<NucleotideSequence> rangeMap = new RangeMap<>();

        public Builder(long fullLength) {
            if(fullLength <0){
                throw new IllegalArgumentException("length must be >= 0");
            }
            this.fullLength = fullLength;
        }

        private final long fullLength;

        public boolean hasSequences(){
            return !rangeMap.isEmpty();
        }
        public Builder addSequence(Range range, NucleotideSequence sequence){
            if(sequence.getLength() != range.getLength()){
                throw new IllegalArgumentException("sequence length must match range length");
            }
            //did anything overlap?
            Map<Range, NucleotideSequence> overlaps = new HashMap<>();
            Map<Range, Range> rangesToRemove = new HashMap<>();
            rangeMap.getAllThatIntersect(range, (r, s, callback)->{
                Range intersection = range.intersection(r);
                Range overlappingSubRange = intersection.shift(-range.getBegin());
                overlaps.put(overlappingSubRange,
                                        s.trim(intersection.shift(-r.getBegin())));

                rangesToRemove.put(r, intersection);
            });

            for(Map.Entry<Range, NucleotideSequence> entry : overlaps.entrySet()){
                NucleotideSequence trimmedSequence = sequence.trim(entry.getKey());
                if(!entry.getValue().equals(trimmedSequence)){
                    throw new IllegalArgumentException("overlapping sequence does not have matching sequence : " +
                            entry.getKey().shift(range.getBegin()).toString((a,b)-> (a+1)+".."+(b+1) +
                                    entry.getValue() + "  vs " + trimmedSequence));
                }

            }
            if(rangesToRemove.isEmpty()) {
                rangeMap.put(range, sequence);
            }else{
                NucleotideSequenceBuilder seqBuilder = sequence.toBuilder();
                Range.Builder rangeBuilder = range.toBuilder();
                boolean shouldReplace=false;
                for(Map.Entry<Range,Range> entry : rangesToRemove.entrySet()){
                    if(range.isSubRangeOf(entry.getKey())){
                        //completely contained and with matching sequence do nothing
                        continue;
                    }
                    NucleotideSequence removedSequence = rangeMap.remove(entry.getKey());
                    long shift = entry.getValue().getBegin() - range.getBegin();


                    if(entry.getKey().startsBefore(range)){
                        //old range is before
                        rangeBuilder.setBegin(entry.getKey().getBegin());
                        seqBuilder.prepend(removedSequence.toBuilder(Range.of(0,range.getBegin() - entry.getKey().getBegin() -1)));
                        shouldReplace=true;
                    }else if(entry.getKey().endsAfter(range)){
                        //old range is after
                        rangeBuilder.setEnd(entry.getKey().getEnd());
                        seqBuilder.append(removedSequence.toBuilder(Range.of(entry.getValue().getEnd()+1-entry.getKey().getBegin(),
                                                                            removedSequence.getLength()-1 )));
                        shouldReplace=true;
                    }
                }
                if(shouldReplace) {
                    rangeMap.put(rangeBuilder.build(), seqBuilder.build());
                }
            }
            return this;
        }


        public PartialNucleotideSequence build(){
//            if(rangeMap.isEmpty()){
//                throw new IllegalStateException("must provide at least one sequence");
//            }

            return new PartialNucleotideSequence(fullLength, rangeMap);
        }
    }
    private final long fullLength;
    private final RangeMap<NucleotideSequence> rangeMap;

    private PartialNucleotideSequence(long fullLength, RangeMap<NucleotideSequence> rangeMap) {
        this.rangeMap = rangeMap;
        this.fullLength = fullLength;
    }

    public static Builder builder(long fullSequenceLength){
        return new Builder(fullSequenceLength);
    }

    @Override
    public NucleotideSequence toNucleotideSequence() {
        return null;
    }

    @Override
    public PartialNucleotideSequence trim(Range trimRange) {
        Builder builder = new Builder(trimRange.getLength());
        rangeMap.getAllThatIntersect(trimRange, (r, seq, callback)->{
            Range intersection = trimRange.intersection(r);
            NucleotideSequence trimmedSeq = seq.trim(intersection.shift(-r.getBegin()));
            builder.addSequence(intersection.shift(-trimRange.getBegin()), trimmedSeq);
        });
        return builder.build();
    }

    @Override
    public List<Integer> getGapOffsets() {
        return getRangesOfGaps().stream()
                .flatMap(r-> StreamUtil.forIterator(r.iterator()))
                .map(Long::intValue)
                .collect(Collectors.toList());
    }

    @Override
    public List<Range> getRangesOfGaps() {
        List<Range> allGapRanges = new ArrayList<>();
        rangeMap.getAllThatIntersect(rangeMap.computeMergedRanges(), (range, seq, callback)->{
            allGapRanges.addAll(seq.getRangesOfGaps()
                                .stream()
                                .map(r-> r.shift(range.getBegin()))
                                .collect(Collectors.toList()));
        });
        return allGapRanges;
    }

    @Override
    public int getNumberOfGaps() {
        return (int) getRangesOfGaps().stream()
                .mapToLong(Range::getLength)
                .sum();
    }

    @Override
    public boolean isGap(int gappedOffset) {
        AtomicBoolean isGap = new AtomicBoolean(false);
        rangeMap.getAllThatIntersect(Range.of(gappedOffset), (r, seq, callback)->{
            int adjustedOffset= (int)(gappedOffset - r.getBegin());
            if(seq.isGap(adjustedOffset)){
                isGap.set(true);
            }
        });
        return isGap.get();
    }

    @Override
    public long getUngappedLength() {
        return fullLength - getNumberOfGaps();
    }

    @Override
    public int getNumberOfGapsUntil(int gappedOffset) {
        SingleThreadAdder counter = new SingleThreadAdder();
        rangeMap.forEach((range, seq)->{
                if(range.getBegin() >gappedOffset) {
                    return;
                }
                if (gappedOffset > range.getEnd()) {
                    counter.add(seq.getNumberOfGaps());
                } else if (range.getBegin() <= gappedOffset) {
                    int shift = (int) (gappedOffset - range.getBegin());
                    int numberOfGapsUntil = seq.getNumberOfGapsUntil(shift);
                    counter.add(numberOfGapsUntil);
                }


        });
        return counter.intValue();
    }

    @Override
    public int getUngappedOffsetFor(int gappedOffset) {
        if(gappedOffset < 0 || gappedOffset >= getLength()){
            throw new IllegalArgumentException("gappedOffset must be between [0, length() )");
        }
        return getUngappedOffsetForSafe(gappedOffset);
    }

    @Override
    public int getUngappedOffsetForSafe(int gappedOffset) {
        //assume ranges are gapped
        SingleThreadAdder numGaps = new SingleThreadAdder();
        rangeMap.getAllThatIntersect(Range.ofLength(gappedOffset+1), (r, seq, callback)->{
            if(r.getEnd() <= gappedOffset){
                numGaps.add(seq.getNumberOfGaps());
            }else {
                int shift = (int)(gappedOffset - r.getBegin());
                numGaps.add(seq.getNumberOfGapsUntil(shift));
            }
        });
        return gappedOffset - numGaps.intValue();
    }

    @Override
    public int getGappedOffsetFor(int ungappedOffset) {
        SingleThreadAdder adjustedValue = new SingleThreadAdder(ungappedOffset);
        AtomicBoolean done = new AtomicBoolean(false);
        rangeMap.forEach((range, seq)->{
            if(done.get()){
                return;
            }

            //ranges are in gapped offsets
            //and are now iterated in order
            if(range.getBegin() > adjustedValue.intValue() ){
                //we are now past
                done.set(true);
                return;
            }
            for(Range gapRange : seq.getRangesOfGaps().stream().map(r -> r.shift(range.getBegin())).collect(Collectors.toList())){
                if(gapRange.getBegin() <= adjustedValue.intValue() ){
                    adjustedValue.add(gapRange.getLength());
                }else{
                    return;
                }
            }

        });
        return adjustedValue.intValue();
    }

    @Override
    public Nucleotide get(long offset) {
        Nucleotide[] n = new Nucleotide[]{Nucleotide.Unknown};
        rangeMap.getAllThatIntersect(Range.of(offset), (r, seq, callback)->{
            long adjusted = offset - r.getBegin();
            n[0] = seq.get(adjusted);
        });
        return n[0];

    }

    @Override
    public long getLength() {
        return fullLength;
    }


    @Override
    public PartialNucleotideSequenceBuilder toBuilder(int initialCapacity) {
        return new PartialNucleotideSequenceBuilder(this);
    }

    @Override
    public PartialNucleotideSequenceBuilder toBuilder() {
        return new PartialNucleotideSequenceBuilder(this);
    }

    @Override
    public PartialNucleotideSequenceBuilder toBuilder(Range range) {
        return null;
    }

    @Override
    public PartialNucleotideSequenceBuilder newEmptyBuilder() {
        PartialNucleotideSequenceBuilder builder= new PartialNucleotideSequenceBuilder(this);
        builder.fullLength=0;
        builder.builders.clear();;
        return builder;
    }

    @Override
    public PartialNucleotideSequenceBuilder newEmptyBuilder(int initialCapacity) {
        return null;
    }

    @Override
    public PartialNucleotideSequenceBuilder toBuilder(List<Range> ranges) {
        return null;
    }

    @Override
    public PartialNucleotideSequence asSubtype() {
        return this;
    }

    @Override
    public Stream<Range> findMatches(Pattern pattern) {
        List<Range> ranges = new ArrayList<>();
        rangeMap.forEach((range, seq)->{
            ranges.addAll(seq.findMatches(pattern)
                    .map(r-> r.shift(range.getBegin()))
                    .collect(Collectors.toList()));
        });
        return ranges.stream();
    }

    @Override
    public Stream<Range> findMatches(Pattern pattern, Range subSequenceRange) {
        List<Range> ranges = new ArrayList<>();
        rangeMap.getAllThatIntersect(subSequenceRange, (r, seq, callback)->{
            Range intersection = subSequenceRange.intersection(r);
            Range shiftedRange = intersection.shift(-r.getBegin());
            ranges.addAll(seq.findMatches(pattern, shiftedRange)
                    .map(r2-> r2.shift(r.getBegin()))
                    .collect(Collectors.toList()));

        });
        return ranges.stream();
    }



    @Override
    public boolean isDna() {
        List<Range> rangesOfCoverage = rangeMap.computeMergedRanges();
        Set<Boolean> areDnas = new HashSet<>();
        rangeMap.getAllThatIntersect(rangesOfCoverage, (r, seq, callback)->{
            areDnas.add(seq.isDna());
        });
        if(areDnas.size()==1){
            return areDnas.iterator().next();
        }
        return false;
    }

    @Override
    public List<Range> getRangesOfNs() {
        List<Range> rangesOfCoverage = rangeMap.computeMergedRanges();
        List<Range> rangesOfNs = Ranges.complement(Range.ofLength(getLength()), rangesOfCoverage);
        rangeMap.getAllThatIntersect(rangesOfCoverage, (r, seq, callback)->{
            rangesOfNs.addAll(seq.getRangesOfNs().stream()
                            .map(range-> range.shift(r.getBegin()))
                    .collect(Collectors.toList()));

        });

        return Ranges.merge(rangesOfNs);
    }
    @Override
    public Iterator<Nucleotide> iterator(Range range) {
        List<Range> rangesOfCoverage = Ranges.union(range, rangeMap.computeMergedRanges());
        List<Range> rangesOfNs = Ranges.union(range, Ranges.complement(Range.ofLength(getLength()), rangesOfCoverage));

        List<RangedIteratorSupplier> list = new ArrayList<>(rangesOfCoverage.size() + rangesOfNs.size());

        rangesOfNs.stream().map(r -> new RangedIteratorSupplier(r, ()->new IteratorOfNs((int) r.getLength())))
                .forEach(list::add);

        rangeMap.getAllThatIntersect(rangesOfCoverage, (r, seq, callback)->{
            Range intersection = range.intersection(r);
            list.add(new RangedIteratorSupplier(intersection,()-> seq.iterator(intersection.shift(-r.getBegin()))));
        });

        list.sort(null);
        return new SortedRangedIterator(list);
    }

    @Override
    public Iterator<Nucleotide> iterator() {
        List<Range> rangesOfCoverage = rangeMap.computeMergedRanges();
        List<Range> rangesOfNs = Ranges.complement(Range.ofLength(getLength()), rangesOfCoverage);

        List<RangedIteratorSupplier> list = new ArrayList<>(rangesOfCoverage.size() + rangesOfNs.size());

        rangesOfNs.stream().map(r -> new RangedIteratorSupplier(r, ()->new IteratorOfNs((int) r.getLength())))
                .forEach(list::add);

        rangeMap.getAllThatIntersect(rangesOfCoverage, (r, seq, callback)->{
            list.add(new RangedIteratorSupplier(r, seq::iterator));
        });

        list.sort(null);
        return new SortedRangedIterator(list);
    }

    private static class SortedRangedIterator implements Iterator<Nucleotide>{
        private final Deque<RangedIteratorSupplier> sortedList;

        PeekableIterator<Nucleotide>  current;
        public SortedRangedIterator(List<RangedIteratorSupplier> sortedList) {
            this.sortedList = new ArrayDeque<>(sortedList);
            advanceToNextIterator();
        }

        private void advanceToNextIterator(){
            current=null;
            RangedIteratorSupplier nextSupplier=sortedList.poll();
            while(nextSupplier !=null){
                 current= IteratorUtil.createPeekableIterator(nextSupplier.iteratorSupplier.get());
                 if(current.hasNext()){
                     break;
                 }
                 current=null;
                nextSupplier=sortedList.poll();
            }
        }

        @Override
        public boolean hasNext() {
            if(current==null){
                return false;
            }
            if(current.hasNext()){
                return true;
            }
            //if we're here we have a current iterator without a next
            advanceToNextIterator();
            return hasNext();
        }

        @Override
        public Nucleotide next() {
            if(!hasNext()){
                throw new NoSuchElementException();
            }
            return current.next();
        }
    }
    private static class RangedIteratorSupplier implements Rangeable, Comparable<RangedIteratorSupplier>{
        private final Range range;

        private final Supplier<Iterator<Nucleotide>> iteratorSupplier;
        public RangedIteratorSupplier(Range range, Supplier<Iterator<Nucleotide>> supplier) {
            this.range = range;
            this.iteratorSupplier = supplier;
        }

        @Override
        public Range asRange() {
            return range;
        }

        @Override
        public int compareTo(RangedIteratorSupplier o) {
            return Range.Comparators.ARRIVAL.compare(range, o.range);
        }
    }
    private static class IteratorOfNs implements Iterator<Nucleotide>{

        private final int numOfNs;
        private int i=0;

        public IteratorOfNs(int numOfNs) {
            this.numOfNs = numOfNs;
        }

        @Override
        public boolean hasNext() {
            return i< numOfNs;
        }

        @Override
        public Nucleotide next() {
            if(!hasNext()){
                throw new NoSuchElementException();
            }
            i++;
            return Nucleotide.Unknown;
        }
    }

    public static class PartialNucleotideSequenceBuilder implements INucleotideSequenceBuilder<PartialNucleotideSequence, PartialNucleotideSequenceBuilder> {

        //TODO implement the other methods we aren't currently using!!!
        Map<Range, NucleotideSequenceBuilder> builders = new TreeMap<>(Range.Comparators.ARRIVAL);

        private long fullLength;


        private PartialNucleotideSequenceBuilder(Map<Range, NucleotideSequenceBuilder> mapToCopy, long length){
            for(Map.Entry<Range,NucleotideSequenceBuilder> entry: mapToCopy.entrySet()){
                builders.put(entry.getKey(), entry.getValue().copy());
            }
            this.fullLength = length;
        }
        private PartialNucleotideSequenceBuilder(PartialNucleotideSequence seq){
            seq.rangeMap.forEach((r,s)->{
                builders.put(r, s.toBuilder());
            });
            this.fullLength = seq.fullLength;
        }
        @Override
        public PartialNucleotideSequenceBuilder insert(int offset, PartialNucleotideSequenceBuilder otherBuilder) {
            Range offsetRange = Range.of(offset);
            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> iter = builders.entrySet().iterator();
            boolean done = false;
            Runnable replacement = ()->{};
            boolean found=false;
            while(!done && iter.hasNext()){
                Map.Entry<Range, NucleotideSequenceBuilder> entry = iter.next();
                if(entry.getKey().intersects(offsetRange)){
                    done = true;
                    found=true;
                    Range entryRange = entry.getKey();
                    NucleotideSequenceBuilder modifiedBuilder = entry.getValue();
                    long oldLength = modifiedBuilder.getLength();
                    modifiedBuilder.insert((int)(offset - entry.getKey().getBegin()), otherBuilder);
                    long newLength = modifiedBuilder.getLength();
                    if(newLength !=oldLength){
                        long shiftAmount = newLength-oldLength;
                        fullLength+=shiftAmount;
                        iter.remove();
                        Runnable oldReplacement = replacement;
                        replacement =  ()->{
                            oldReplacement.run();
                            Range newRange = new Range.Builder(newLength).shift(entryRange.getBegin()).build();
                            //everything past our new range must shift down
                            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                            Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();
                            //first add our modified sequence
                            shifted.put(newRange, modifiedBuilder);
                            //now shift anything downstream
                            while(shiftIter.hasNext()){
                                Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                                if(shiftEntry.getKey().startsAfter(newRange)){
                                    shifted.put(shiftEntry.getKey().shift(shiftAmount), shiftEntry.getValue());
                                    shiftIter.remove();
                                }
                            }

                            builders.putAll(shifted);
                        };
                    }

                }
            }
            if(found) {
                replacement.run();
            }else{
                //not found so add
                Range newRange = new Range.Builder(otherBuilder.fullLength)
                        .shift(offset)
                        .build();

                //everything past our new range must shift down
                Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();

                //now shift anything downstream
                while(shiftIter.hasNext()){
                    Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                    if(shiftEntry.getKey().startsAfter(newRange)){
                        shifted.put(shiftEntry.getKey().shift(newRange.getLength()), shiftEntry.getValue());
                        shiftIter.remove();
                    }
                }

                //add ranges from other builder
                for(Map.Entry<Range, NucleotideSequenceBuilder> otherEntry : otherBuilder.builders.entrySet()){
                    builders.put(otherEntry.getKey().shift(offset), otherEntry.getValue());
                }
                builders.putAll(shifted);
                fullLength+= newRange.getLength();
            }
            mergeOverlappingRanges();
            return this;
        }
        @Override
        public PartialNucleotideSequenceBuilder insert(int offset, NucleotideSequence sequence) {
            Range offsetRange = new Range.Builder(sequence.getLength()).shift(offset).build();
            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> iter = builders.entrySet().iterator();
            boolean done = false;
            Runnable replacement = ()->{};
            boolean found=false;
            while(!done && iter.hasNext()){
                Map.Entry<Range, NucleotideSequenceBuilder> entry = iter.next();
                if(entry.getKey().intersects(offsetRange)){

                    Range entryRange = entry.getKey();
                    NucleotideSequenceBuilder modifiedBuilder = entry.getValue();
                    long oldLength = modifiedBuilder.getLength();

                    int insertionRelativeOffset = (int) (offset - entry.getKey().getBegin());
                    if(insertionRelativeOffset >= 0){
                        found=true;
                        modifiedBuilder.insert(insertionRelativeOffset, sequence);
                    }

                    long newLength = modifiedBuilder.getLength();
                    if(newLength !=oldLength){
                        long shiftAmount = newLength-oldLength;
                        fullLength+=shiftAmount;
                        iter.remove();
                        Runnable oldReplacement = replacement;
                        replacement =  ()->{
                            oldReplacement.run();
                            Range newRange = new Range.Builder(newLength).shift(entryRange.getBegin()).build();
                            //everything past our new range must shift down
                            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                            Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();
                            //first add our modified sequence
                            shifted.put(newRange, modifiedBuilder);
                            //now shift anything downstream
                            while(shiftIter.hasNext()){
                                Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                                if(shiftEntry.getKey().startsAfter(newRange)){
                                    shifted.put(shiftEntry.getKey().shift(shiftAmount), shiftEntry.getValue());
                                    shiftIter.remove();
                                }
                            }

                            builders.putAll(shifted);
                        };
                    }

                }
            }
            if(found) {
                replacement.run();
            }else{
                //not found so add

                //everything past our new range must shift down
                Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();
                //first add our modified sequence
                shifted.put(offsetRange, sequence.toBuilder());
                //now shift anything downstream
                while(shiftIter.hasNext()){
                    Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                    if(shiftEntry.getKey().startsAfter(offsetRange)){
                        shifted.put(shiftEntry.getKey().shift(offsetRange.getLength()), shiftEntry.getValue());
                        shiftIter.remove();
                    }
                }

                builders.putAll(shifted);
                fullLength+= sequence.getLength();
            }
            mergeOverlappingRanges();
            return this;
        }

        @Override
        public PartialNucleotideSequenceBuilder insert(int offset, String sequence) {
            return insert(offset, NucleotideSequence.of(sequence));
        }
        @Override
        public PartialNucleotideSequenceBuilder insert(int offset, Nucleotide[] sequence) {
            Range offsetRange = new Range.Builder(sequence.length).shift(offset).build();
            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> iter = builders.entrySet().iterator();
            boolean done = false;
            Runnable replacement = ()->{};
            boolean found=false;
            while(!done && iter.hasNext()){
                Map.Entry<Range, NucleotideSequenceBuilder> entry = iter.next();
                if(entry.getKey().intersects(offsetRange)){

                    Range entryRange = entry.getKey();
                    NucleotideSequenceBuilder modifiedBuilder = entry.getValue();
                    long oldLength = modifiedBuilder.getLength();

                    int insertionRelativeOffset = (int) (offset - entry.getKey().getBegin());
                    if(insertionRelativeOffset >= 0){
                        found=true;
                        modifiedBuilder.insert(insertionRelativeOffset, sequence);
                    }

                    long newLength = modifiedBuilder.getLength();
                    if(newLength !=oldLength){
                        long shiftAmount = newLength-oldLength;
                        fullLength+=shiftAmount;
                        iter.remove();
                        Runnable oldReplacement = replacement;
                        replacement =  ()->{
                            oldReplacement.run();
                            Range newRange = new Range.Builder(newLength).shift(entryRange.getBegin()).build();
                            //everything past our new range must shift down
                            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                            Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();
                            //first add our modified sequence
                            shifted.put(newRange, modifiedBuilder);
                            //now shift anything downstream
                            while(shiftIter.hasNext()){
                                Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                                if(shiftEntry.getKey().startsAfter(newRange)){
                                    shifted.put(shiftEntry.getKey().shift(shiftAmount), shiftEntry.getValue());
                                    shiftIter.remove();
                                }
                            }

                            builders.putAll(shifted);
                        };
                    }

                }
            }
            if(found) {
                replacement.run();
            }else{
                //not found so add
                NucleotideSequenceBuilder newBuilder = new NucleotideSequenceBuilder(sequence.length);
                newBuilder.append(sequence);

                //everything past our new range must shift down
                Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();
                //first add our modified sequence
                shifted.put(offsetRange, newBuilder);
                //now shift anything downstream
                while(shiftIter.hasNext()){
                    Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                    if(shiftEntry.getKey().startsAfter(offsetRange)){
                        shifted.put(shiftEntry.getKey().shift(offsetRange.getLength()), shiftEntry.getValue());
                        shiftIter.remove();
                    }
                }

                builders.putAll(shifted);
                fullLength+= newBuilder.getLength();
            }
            mergeOverlappingRanges();
            return this;

        }

        @Override
        public PartialNucleotideSequenceBuilder insert(int offset, char[] sequence) {
            Range offsetRange = new Range.Builder(sequence.length).shift(offset).build();
            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> iter = builders.entrySet().iterator();
            boolean done = false;
            Runnable replacement = ()->{};
            boolean found=false;
            while(!done && iter.hasNext()){
                Map.Entry<Range, NucleotideSequenceBuilder> entry = iter.next();
                if(entry.getKey().intersects(offsetRange)){

                    Range entryRange = entry.getKey();
                    NucleotideSequenceBuilder modifiedBuilder = entry.getValue();
                    long oldLength = modifiedBuilder.getLength();

                    int insertionRelativeOffset = (int) (offset - entry.getKey().getBegin());
                    if(insertionRelativeOffset >= 0){
                        found=true;
                        modifiedBuilder.insert(insertionRelativeOffset, sequence);
                    }

                    long newLength = modifiedBuilder.getLength();
                    if(newLength !=oldLength){
                        long shiftAmount = newLength-oldLength;
                        fullLength+=shiftAmount;
                        iter.remove();
                        Runnable oldReplacement = replacement;
                        replacement =  ()->{
                            oldReplacement.run();
                            Range newRange = new Range.Builder(newLength).shift(entryRange.getBegin()).build();
                            //everything past our new range must shift down
                            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                            Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();
                            //first add our modified sequence
                            shifted.put(newRange, modifiedBuilder);
                            //now shift anything downstream
                            while(shiftIter.hasNext()){
                                Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                                if(shiftEntry.getKey().startsAfter(newRange)){
                                    shifted.put(shiftEntry.getKey().shift(shiftAmount), shiftEntry.getValue());
                                    shiftIter.remove();
                                }
                            }

                            builders.putAll(shifted);
                        };
                    }

                }
            }
            if(found) {
                replacement.run();
            }else{
                //not found so add
                NucleotideSequenceBuilder newBuilder = new NucleotideSequenceBuilder(sequence.length);
                newBuilder.append(sequence);

                //everything past our new range must shift down
                Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> shiftIter = builders.entrySet().iterator();
                Map<Range, NucleotideSequenceBuilder> shifted = new HashMap<>();
                //first add our modified sequence
                shifted.put(offsetRange, newBuilder);
                //now shift anything downstream
                while(shiftIter.hasNext()){
                    Map.Entry<Range, NucleotideSequenceBuilder> shiftEntry = shiftIter.next();
                    if(shiftEntry.getKey().startsAfter(offsetRange)){
                        shifted.put(shiftEntry.getKey().shift(offsetRange.getLength()), shiftEntry.getValue());
                        shiftIter.remove();
                    }
                }

                builders.putAll(shifted);
                fullLength+= newBuilder.getLength();
            }
            mergeOverlappingRanges();
            return this;
        }

        private void mergeOverlappingRanges(){


            List<Range> mergedRanges = Ranges.merge( builders.keySet());
                if(mergedRanges.size() != builders.size()) {
                    Map<Range, NucleotideSequenceBuilder> mergedBuilders = new HashMap<>();
                    for (Range mergedRange : mergedRanges) {
                        Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> builderIter = builders.entrySet().iterator();
                        while (builderIter.hasNext()) {
                            Map.Entry<Range, NucleotideSequenceBuilder> builder = builderIter.next();

                            Range intersection = builder.getKey().intersection(mergedRange);
                            if (intersection.isNotEmpty()) {
                                NucleotideSequenceBuilder mergedBuilder = mergedBuilders.computeIfAbsent(mergedRange, k -> createNs((int) k.getLength()));
                                Range shift = intersection.shift(-mergedRange.getBegin());
                                mergedBuilder.replace(shift, builder.getValue());

                                builderIter.remove();
                            }
                        }
                    }

                    builders.putAll(mergedBuilders);
                }
        }

        private static NucleotideSequenceBuilder createNs(int length){
            Nucleotide[] ns = new Nucleotide[length];
            Arrays.fill(ns , Nucleotide.Unknown);
            return new NucleotideSequenceBuilder(length).append(ns);
        }

        @Override
        public PartialNucleotideSequenceBuilder insert(int offset, Nucleotide base) {
            Range offsetRange = Range.of(offset);
            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> iter = builders.entrySet().iterator();
            boolean done = false;
            Runnable replacement = ()->{};
            boolean found=false;
            while(!done && iter.hasNext()){
                Map.Entry<Range, NucleotideSequenceBuilder> entry = iter.next();
                if(entry.getKey().intersects(offsetRange)){
                    done = true;
                    found=true;
                    Range entryRange = entry.getKey();
                    NucleotideSequenceBuilder modifiedBuilder = entry.getValue();
                    long oldLength = modifiedBuilder.getLength();
                    modifiedBuilder.insert((int)(offset - entry.getKey().getBegin()), base);
                    long newLength = modifiedBuilder.getLength();
                    if(newLength !=oldLength){
                        fullLength-= newLength-oldLength;
                        iter.remove();
                        replacement = ()->{
                            builders.put(new Range.Builder(newLength).shift( entryRange.getBegin()).build(), modifiedBuilder);
                        };
                    }

                }
            }
            if(found) {
                replacement.run();
            }else{
                //not found so add
                NucleotideSequenceBuilder newBuilder = new NucleotideSequenceBuilder();
                newBuilder.append(base);
                builders.put(offsetRange, newBuilder);
                fullLength+=1;
            }
            return this;
        }
        @Override
        public PartialNucleotideSequenceBuilder insert(int offset, Iterable<Nucleotide> sequence) {
            return  insert(offset, new NucleotideSequenceBuilder(sequence).toArray());
        }
        @Override
        public PartialNucleotideSequenceBuilder prepend(Iterable<Nucleotide> sequence) {
            return insert(0, sequence);

        }

        @Override
        public PartialNucleotideSequenceBuilder prepend(PartialNucleotideSequenceBuilder otherBuilder) {
            return insert(0, otherBuilder);
        }

        @Override
        public PartialNucleotideSequence build() {
            PartialNucleotideSequence.Builder innerBuilder = new Builder(fullLength);


            builders.forEach((r,builder)-> innerBuilder.addSequence(r, builder.build()));
            return innerBuilder.build();
        }

        @Override
        public PartialNucleotideSequenceBuilder trim(Range range) {
            Range currentLengthRange = Range.ofLength(fullLength);
            Range trimRange = range.intersection(currentLengthRange);
            fullLength = trimRange.getLength();
            Map<Range, NucleotideSequenceBuilder> newBuilders = new TreeMap<>(Range.Comparators.ARRIVAL);
            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> iter = builders.entrySet().iterator();
            while(iter.hasNext()){
                Map.Entry<Range, NucleotideSequenceBuilder> entry = iter.next();
                Range intersection = entry.getKey().intersection(trimRange);

                if(intersection.isNotEmpty()){
                    newBuilders.put( intersection.shift(range.getBegin()),
                            entry.getValue().trim(intersection.shift(intersection.getBegin() - entry.getKey().getBegin()))
                            );
                }
                iter.remove();
            }
            builders = newBuilders;
            return this;
        }

        @Override
        public PartialNucleotideSequenceBuilder copy() {
            return new PartialNucleotideSequenceBuilder(builders, fullLength);
        }

        @Override
        public PartialNucleotideSequenceBuilder copy(Range range) {
            return copy().trim(range);
        }

        @Override
        public PartialNucleotideSequenceBuilder reverse() {
            return null;
        }

        @Override
        public Iterator<Nucleotide> iterator() {
            return build().iterator();
        }

        @Override
        public PartialNucleotideSequenceBuilder clear() {
             builders.clear();
             fullLength=0;
             return this;
        }

        @Override
        public PartialNucleotideSequenceBuilder ungap() {
            Iterator<Map.Entry<Range, NucleotideSequenceBuilder>> iter = builders.entrySet().iterator();
            Map<Range, NucleotideSequenceBuilder> adjustedMap = new TreeMap<>(Range.Comparators.ARRIVAL);

            int numGapsAdjustedSoFar=0;
            while(iter.hasNext()){
                Map.Entry<Range, NucleotideSequenceBuilder> entry = iter.next();
                int numGaps = entry.getValue().getNumGaps();
                if(entry.getValue().getNumGaps() >0){
                    Range newRange = new Range.Builder(entry.getKey())
                            .shift(-numGapsAdjustedSoFar)
                                    .contractEnd(numGaps)
                                            .build();
                    adjustedMap.put(newRange,  entry.getValue().ungap());
                    numGapsAdjustedSoFar+=numGaps;
                   iter.remove();
                }
            }
            builders.putAll(adjustedMap);
            fullLength-= numGapsAdjustedSoFar;
            return this;
        }

        @Override
        public PartialNucleotideSequenceBuilder turnOffDataCompression(boolean turnOffDataCompression) {
            return this;
        }



        @Override
        public PartialNucleotideSequenceBuilder append(char[] sequence) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder append(Nucleotide residue) {
            return null;
        }

        @Override
        public Nucleotide get(int offset) {
            if(offset < 0 || offset > fullLength){
                throw new IndexOutOfBoundsException();
            }
            for(Map.Entry<Range, NucleotideSequenceBuilder> entry : builders.entrySet()){
                if(entry.getKey().intersects(offset)){
                    int adjusted = (int)(offset - entry.getKey().getBegin());
                    return entry.getValue().get(adjusted);
                }
            }
            return Nucleotide.Unknown;
        }

        @Override
        public PartialNucleotideSequenceBuilder append(Iterable<Nucleotide> sequence) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder append(Nucleotide[] sequence) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder append(String sequence) {
            return null;
        }



        @Override
        public long getLength() {
            return fullLength;
        }

        @Override
        public long getUngappedLength() {
            return fullLength - builders.values().stream().mapToLong(NucleotideSequenceBuilder::getNumGaps).sum();
        }

        @Override
        public PartialNucleotideSequenceBuilder replace(int offset, Nucleotide replacement) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder append(NucleotideSequence sequence) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder append(NucleotideSequence sequence, Range range) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder append(NucleotideSequenceBuilder otherBuilder) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder setInvalidCharacterHandler(Nucleotide.InvalidCharacterHandler invalidCharacterHandler) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder setDecodingOptions(NucleotideSequenceBuilder.DecodingOptions decodingOptions) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder prepend(Nucleotide n) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder prepend(NucleotideSequence sequence) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder reverseComplement() {
            return null;
        }

        @Override
        public Range toGappedRange(Range ungappedRange) {
            return null;
        }

        @Override
        public Range toUngappedRange(Range gappedRange) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder replace(Range range, PartialNucleotideSequenceBuilder otherBuilder) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder delete(Range range) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder getSelf() {
            return this;
        }

        @Override
        public int getNumGaps() {
            return (int) builders.values().stream()
                            .mapToLong(NucleotideSequenceBuilder::getNumGaps)
                            .sum();
        }

        @Override
        public PartialNucleotideSequenceBuilder prepend(String sequence) {
            return null;
        }



        @Override
        public PartialNucleotideSequenceBuilder replace(Range range, Nucleotide[] replacementSequence) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder replace(Range offset, PartialNucleotideSequence replacement) {
            return null;
        }

        @Override
        public PartialNucleotideSequenceBuilder replace(Range range, NucleotideSequence replacementSequence) {
            return null;
        }

        @Override
        public IntStream gaps() {
            return builders.entrySet()
                    .stream().flatMapToInt(entry->
                        entry.getValue().gaps()
                                .map(offset-> (int) (offset+entry.getKey().getBegin()))

                    );
        }
    }
}
